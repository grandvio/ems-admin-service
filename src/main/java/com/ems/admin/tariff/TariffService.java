package com.ems.admin.tariff;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.asset.service.StationGuard;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.tariff.entity.EmsMarketPrice;
import com.ems.admin.tariff.entity.EmsPriceLocation;
import com.ems.admin.tariff.entity.EmsStationTariff;
import com.ems.admin.tariff.mapper.EmsMarketPriceMapper;
import com.ems.admin.tariff.mapper.EmsPriceLocationMapper;
import com.ems.admin.tariff.mapper.EmsStationTariffMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TariffService {
    private final EmsPriceLocationMapper locationMapper;
    private final EmsMarketPriceMapper priceMapper;
    private final EmsStationTariffMapper tariffMapper;
    private final EmsStationMapper stationMapper;
    private final StationGuard stationGuard;

    public TariffService(EmsPriceLocationMapper locationMapper, EmsMarketPriceMapper priceMapper,
                         EmsStationTariffMapper tariffMapper, EmsStationMapper stationMapper,
                         StationGuard stationGuard) {
        this.locationMapper = locationMapper;
        this.priceMapper = priceMapper;
        this.tariffMapper = tariffMapper;
        this.stationMapper = stationMapper;
        this.stationGuard = stationGuard;
    }

    public List<TariffDtos.LocationVO> locations(String market) {
        LambdaQueryWrapper<EmsPriceLocation> wrapper = new LambdaQueryWrapper<EmsPriceLocation>()
                .orderByAsc(EmsPriceLocation::getId);
        if (StringUtils.hasText(market)) {
            String normalized = market.trim().toUpperCase();
            if (!"EU".equals(normalized) && !"US".equals(normalized)) {
                throw BizException.badRequest("市场只能是 EU 或 US");
            }
            wrapper.eq(EmsPriceLocation::getMarket, normalized);
        }
        return locationMapper.selectList(wrapper).stream().map(this::toLocation).toList();
    }

    public TariffDtos.TariffVO get(Long stationId) {
        requireStation(stationId);
        EmsStationTariff tariff = find(stationId);
        return tariff == null ? null : toVo(tariff);
    }

    @Transactional
    public void save(Long stationId, TariffDtos.TariffRequest request) {
        EmsStation station = requireStation(stationId);
        EmsPriceLocation buyLocation = side(request.currency(), request.buyMode(), request.buyFixedPrice(), request.buyLocationId(), "买电");
        EmsPriceLocation sellLocation = side(request.currency(), request.sellMode(), request.sellFixedPrice(), request.sellLocationId(), "卖电");
        EmsStationTariff tariff = find(stationId);
        if (tariff == null) {
            tariff = new EmsStationTariff();
            tariff.setTenantId(station.getTenantId() == null ? TenantContext.get() : station.getTenantId());
            tariff.setStationId(stationId);
            fill(tariff, request, buyLocation, sellLocation);
            tariffMapper.insert(tariff);
            return;
        }
        fill(tariff, request, buyLocation, sellLocation);
        tariffMapper.updateById(tariff);
    }

    public TariffDtos.PriceChart prices(Long stationId, LocalDate date, String product) {
        EmsStation station = requireStation(stationId);
        if (date == null) {
            throw BizException.badRequest("请指定日期");
        }
        EmsStationTariff tariff = find(stationId);
        if (tariff == null) {
            throw BizException.badRequest("请先配置电价结算");
        }
        String override = StringUtils.hasText(product) ? resolveProduct(product) : null;
        ZoneId zone = zone(station.getTimezone());
        LocalDateTime start = date.atStartOfDay();
        String buyProduct = override == null ? tariff.getBuyProduct() : override;
        String sellProduct = override == null ? tariff.getSellProduct() : override;
        List<PriceHours.Interval> buyIntervals = intervals(tariff.getBuyMode(), tariff.getBuyLocationId(), buyProduct, true, start, zone);
        List<PriceHours.Interval> sellIntervals = intervals(tariff.getSellMode(), tariff.getSellLocationId(), sellProduct, false, start, zone);
        Long chartLocationId = chartLocation(tariff);
        List<PriceHours.Interval> dayAheadIntervals = chartIntervals(chartLocationId, EmsMarketPrice.DAY_AHEAD, start, zone);
        List<PriceHours.Interval> realTimeIntervals = chartIntervals(chartLocationId, EmsMarketPrice.REAL_TIME, start, zone);
        List<String> buckets = new ArrayList<>(24);
        List<BigDecimal> dayAhead = new ArrayList<>(24);
        List<BigDecimal> realTime = new ArrayList<>(24);
        List<BigDecimal> marketBuy = new ArrayList<>(24);
        List<BigDecimal> marketSell = new ArrayList<>(24);
        List<BigDecimal> buy = new ArrayList<>(24);
        List<BigDecimal> sell = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            LocalDateTime hourStart = start.plusHours(hour);
            buckets.add(String.format("%02d", hour));
            dayAhead.add(chartLocationId == null ? null : PriceHours.averagePerKwh(dayAheadIntervals, hourStart, zone));
            realTime.add(chartLocationId == null ? null : PriceHours.averagePerKwh(realTimeIntervals, hourStart, zone));
            BigDecimal buyMarket = EmsStationTariff.MARKET.equals(tariff.getBuyMode())
                    ? PriceHours.averagePerKwh(buyIntervals, hourStart, zone) : null;
            BigDecimal sellMarket = EmsStationTariff.MARKET.equals(tariff.getSellMode())
                    ? PriceHours.averagePerKwh(sellIntervals, hourStart, zone) : null;
            marketBuy.add(buyMarket);
            marketSell.add(sellMarket);
            buy.add(PriceHours.settle(tariff.getBuyMode(), tariff.getBuyFixedPrice(), buyMarket, tariff.getBuyAdder()));
            sell.add(PriceHours.settle(tariff.getSellMode(), tariff.getSellFixedPrice(), sellMarket, tariff.getSellAdder()));
        }
        TariffDtos.PriceChart chart = new TariffDtos.PriceChart();
        chart.setCurrency(tariff.getCurrency());
        chart.setUnit("kWh");
        chart.setBuckets(buckets);
        chart.setDayAheadPrice(dayAhead);
        chart.setRealTimePrice(realTime);
        chart.setMarketBuyPrice(marketBuy);
        chart.setMarketSellPrice(marketSell);
        chart.setBuyPrice(buy);
        chart.setSellPrice(sell);
        return chart;
    }

    private Long chartLocation(EmsStationTariff tariff) {
        if (EmsStationTariff.MARKET.equals(tariff.getBuyMode()) && tariff.getBuyLocationId() != null) {
            return tariff.getBuyLocationId();
        }
        if (EmsStationTariff.MARKET.equals(tariff.getSellMode()) && tariff.getSellLocationId() != null) {
            return tariff.getSellLocationId();
        }
        return null;
    }

    private List<PriceHours.Interval> chartIntervals(Long locationId, String product, LocalDateTime dayStart, ZoneId zone) {
        if (locationId == null) {
            return List.of();
        }
        return intervals(EmsStationTariff.MARKET, locationId, product, true, dayStart, zone);
    }

    public java.util.Map<LocalDateTime, CostRollup.Prices> priceSlots(EmsStation station, EmsStationTariff tariff,
                                                                       LocalDateTime start, LocalDateTime end) {
        ZoneId zone = zone(station.getTimezone());
        List<PriceHours.Interval> buyIntervals = intervals(tariff.getBuyMode(), tariff.getBuyLocationId(), tariff.getBuyProduct(), true, start, end, zone);
        List<PriceHours.Interval> sellIntervals = intervals(tariff.getSellMode(), tariff.getSellLocationId(), tariff.getSellProduct(), false, start, end, zone);
        java.util.Map<LocalDateTime, CostRollup.Prices> slots = new java.util.HashMap<>();
        for (LocalDateTime hour = start; hour.isBefore(end); hour = hour.plusHours(1)) {
            BigDecimal buyMarket = EmsStationTariff.MARKET.equals(tariff.getBuyMode())
                    ? PriceHours.averagePerKwh(buyIntervals, hour, zone) : null;
            BigDecimal sellMarket = EmsStationTariff.MARKET.equals(tariff.getSellMode())
                    ? PriceHours.averagePerKwh(sellIntervals, hour, zone) : null;
            slots.put(hour, new CostRollup.Prices(
                    PriceHours.settle(tariff.getBuyMode(), tariff.getBuyFixedPrice(), buyMarket, tariff.getBuyAdder()),
                    PriceHours.settle(tariff.getSellMode(), tariff.getSellFixedPrice(), sellMarket, tariff.getSellAdder())));
        }
        return slots;
    }

    public EmsStationTariff requireTariff(Long stationId) {
        EmsStationTariff tariff = find(stationId);
        if (tariff == null) {
            throw BizException.badRequest("请先配置电价结算");
        }
        return tariff;
    }

    public EmsStation requireStation(Long stationId) {
        stationGuard.check(stationId);
        EmsStation station = stationMapper.selectById(stationId);
        if (station == null) {
            throw BizException.notFound("电站不存在");
        }
        return station;
    }

    private List<PriceHours.Interval> intervals(String mode, Long locationId, String product, boolean buy,
                                                LocalDateTime dayStart, ZoneId zone) {
        return intervals(mode, locationId, product, buy, dayStart, dayStart.plusDays(1), zone);
    }

    private List<PriceHours.Interval> intervals(String mode, Long locationId, String product, boolean buy,
                                                LocalDateTime start, LocalDateTime end, ZoneId zone) {
        if (!EmsStationTariff.MARKET.equals(mode) || locationId == null) {
            return List.of();
        }
        String resolved = resolveProduct(product);
        LocalDateTime from = start.atZone(zone).minusDays(1).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        LocalDateTime to = end.atZone(zone).plusDays(1).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        List<EmsMarketPrice> rows = priceMapper.selectList(new LambdaQueryWrapper<EmsMarketPrice>()
                .eq(EmsMarketPrice::getLocationId, locationId)
                .eq(EmsMarketPrice::getProduct, resolved)
                .ge(EmsMarketPrice::getIntervalStart, from)
                .lt(EmsMarketPrice::getIntervalStart, to));
        String direction = PriceDirections.choose(rows.stream().map(EmsMarketPrice::getDirection).collect(Collectors.toSet()),
                PriceDirections.preferred(resolved, buy));
        return rows.stream()
                .filter(row -> direction.equals(PriceDirections.normalize(row.getDirection())))
                .map(row -> new PriceHours.Interval(row.getIntervalStart().toInstant(java.time.ZoneOffset.UTC),
                        row.getIntervalMinutes(), row.getPricePerMwh()))
                .toList();
    }

    private String resolveProduct(String product) {
        if (!StringUtils.hasText(product) || EmsMarketPrice.DAY_AHEAD.equals(product)) {
            return EmsMarketPrice.DAY_AHEAD;
        }
        if (EmsMarketPrice.REAL_TIME.equals(product)) {
            return EmsMarketPrice.REAL_TIME;
        }
        throw BizException.badRequest("电价品种只能是日前或实时");
    }

    private EmsPriceLocation side(String currency, String mode, BigDecimal fixedPrice, Long locationId, String label) {
        if (EmsStationTariff.FIXED.equals(mode)) {
            if (fixedPrice == null) {
                throw BizException.badRequest(label + "固定价不能为空");
            }
            return null;
        }
        if (locationId == null) {
            throw BizException.badRequest(label + "需要选择电价地点");
        }
        EmsPriceLocation location = locationMapper.selectById(locationId);
        if (location == null) {
            throw BizException.badRequest("电价地点不存在");
        }
        if (!currency.equals(location.getCurrency())) {
            throw BizException.badRequest("结算货币需与电价地点一致");
        }
        return location;
    }

    private void fill(EmsStationTariff tariff, TariffDtos.TariffRequest request,
                      EmsPriceLocation buyLocation, EmsPriceLocation sellLocation) {
        tariff.setCurrency(request.currency());
        tariff.setBuyMode(request.buyMode());
        tariff.setBuyProduct(storedProduct(request.buyMode(), request.buyProduct()));
        tariff.setBuyFixedPrice(EmsStationTariff.FIXED.equals(request.buyMode()) ? request.buyFixedPrice() : null);
        tariff.setBuyLocationId(buyLocation == null ? null : buyLocation.getId());
        tariff.setBuyAdder(request.buyAdder() == null ? BigDecimal.ZERO : request.buyAdder());
        tariff.setSellMode(request.sellMode());
        tariff.setSellProduct(storedProduct(request.sellMode(), request.sellProduct()));
        tariff.setSellFixedPrice(EmsStationTariff.FIXED.equals(request.sellMode()) ? request.sellFixedPrice() : null);
        tariff.setSellLocationId(sellLocation == null ? null : sellLocation.getId());
        tariff.setSellAdder(request.sellAdder() == null ? BigDecimal.ZERO : request.sellAdder());
    }

    private String storedProduct(String mode, String product) {
        if (!EmsStationTariff.MARKET.equals(mode) || !StringUtils.hasText(product)) {
            return EmsMarketPrice.DAY_AHEAD;
        }
        return resolveProduct(product);
    }

    private EmsStationTariff find(Long stationId) {
        return tariffMapper.selectOne(new LambdaQueryWrapper<EmsStationTariff>().eq(EmsStationTariff::getStationId, stationId));
    }

    private TariffDtos.TariffVO toVo(EmsStationTariff tariff) {
        TariffDtos.TariffVO vo = new TariffDtos.TariffVO();
        vo.setCurrency(tariff.getCurrency());
        vo.setBuyMode(tariff.getBuyMode());
        vo.setBuyProduct(tariff.getBuyProduct());
        vo.setBuyFixedPrice(tariff.getBuyFixedPrice());
        vo.setBuyLocationId(tariff.getBuyLocationId());
        vo.setBuyAdder(tariff.getBuyAdder());
        vo.setSellMode(tariff.getSellMode());
        vo.setSellProduct(tariff.getSellProduct());
        vo.setSellFixedPrice(tariff.getSellFixedPrice());
        vo.setSellLocationId(tariff.getSellLocationId());
        vo.setSellAdder(tariff.getSellAdder());
        EmsPriceLocation buy = tariff.getBuyLocationId() == null ? null : locationMapper.selectById(tariff.getBuyLocationId());
        EmsPriceLocation sell = tariff.getSellLocationId() == null ? null : locationMapper.selectById(tariff.getSellLocationId());
        if (buy != null) {
            vo.setBuyLocationCode(buy.getCode());
            vo.setBuyLocationName(buy.getName());
        }
        if (sell != null) {
            vo.setSellLocationCode(sell.getCode());
            vo.setSellLocationName(sell.getName());
        }
        return vo;
    }

    private TariffDtos.LocationVO toLocation(EmsPriceLocation location) {
        TariffDtos.LocationVO vo = new TariffDtos.LocationVO();
        vo.setId(location.getId());
        vo.setMarket(location.getMarket());
        vo.setCode(location.getCode());
        vo.setName(location.getName());
        vo.setCurrency(location.getCurrency());
        vo.setTimezone(location.getTimezone());
        return vo;
    }

    private ZoneId zone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            return ZoneId.of("Asia/Shanghai");
        }
    }
}
