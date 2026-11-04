package com.ems.admin.tariff;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.framework.config.EmsProperties;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.tariff.entity.EmsMarketPrice;
import com.ems.admin.tariff.entity.EmsPriceLocation;
import com.ems.admin.tariff.entity.EmsStationTariff;
import com.ems.admin.tariff.mapper.EmsPriceLocationMapper;
import com.ems.admin.tariff.mapper.EmsStationTariffMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class EntsoeSyncJob {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private final EmsProperties properties;
    private final EmsStationTariffMapper tariffMapper;
    private final EmsPriceLocationMapper locationMapper;
    private final EntsoePriceClient entsoePriceClient;
    private final MarketPriceStore marketPriceStore;

    public EntsoeSyncJob(EmsProperties properties, EmsStationTariffMapper tariffMapper,
                         EmsPriceLocationMapper locationMapper, EntsoePriceClient entsoePriceClient,
                         MarketPriceStore marketPriceStore) {
        this.properties = properties;
        this.tariffMapper = tariffMapper;
        this.locationMapper = locationMapper;
        this.entsoePriceClient = entsoePriceClient;
        this.marketPriceStore = marketPriceStore;
    }

    @Scheduled(cron = "0 30 14 * * *", zone = "Europe/Berlin")
    public void afternoon() {
        syncNextDay();
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "Europe/Berlin")
    public void evening() {
        syncNextDay();
    }

    public void syncNextDay() {
        if (!properties.getPrice().isSyncEnabled() || !StringUtils.hasText(properties.getPrice().getEntsoeSecurityToken())) {
            return;
        }
        LocalDate day = LocalDate.now(BERLIN).plusDays(1);
        ZonedDateTime start = day.atStartOfDay(BERLIN);
        Instant from = start.toInstant();
        Instant to = start.plusDays(1).toInstant();
        boolean previous = TenantContext.setIgnore(true);
        try {
            for (EmsPriceLocation location : referencedEuLocations()) {
                try {
                    List<EntsoePriceParser.Point> points = entsoePriceClient.fetch(location.getExternalRef(), from, to);
                    marketPriceStore.upsert(location.getId(), location.getCurrency(), EmsMarketPrice.DAY_AHEAD, points);
                } catch (RuntimeException ex) {
                    log.warn("欧洲日前电价拉取失败 location={} msg={}", location.getCode(), ex.getMessage());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("欧洲日前电价同步失败 msg={}", ex.getMessage());
        } finally {
            TenantContext.setIgnore(previous);
        }
    }

    @Scheduled(cron = "0 0 11 * * *", zone = "Europe/Berlin")
    public void imbalanceMorning() {
        syncRecentImbalance();
    }

    public void syncRecentImbalance() {
        if (!properties.getPrice().isSyncEnabled() || !StringUtils.hasText(properties.getPrice().getEntsoeSecurityToken())) {
            return;
        }
        LocalDate today = LocalDate.now(BERLIN);
        boolean previous = TenantContext.setIgnore(true);
        try {
            List<EmsPriceLocation> locations = referencedEuLocations();
            for (int daysAgo = 1; daysAgo <= 2; daysAgo++) {
                LocalDate day = today.minusDays(daysAgo);
                ZonedDateTime start = day.atStartOfDay(BERLIN);
                Instant from = start.toInstant();
                Instant to = start.plusDays(1).toInstant();
                for (EmsPriceLocation location : locations) {
                    try {
                        List<EntsoePriceParser.Point> points = entsoePriceClient.fetchImbalance(location.getExternalRef(), from, to);
                        marketPriceStore.upsert(location.getId(), location.getCurrency(), EmsMarketPrice.REAL_TIME, points);
                    } catch (RuntimeException ex) {
                        log.warn("欧洲不平衡电价拉取失败 location={} day={} msg={}", location.getCode(), day, ex.getMessage());
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("欧洲不平衡电价同步失败 msg={}", ex.getMessage());
        } finally {
            TenantContext.setIgnore(previous);
        }
    }

    private List<EmsPriceLocation> referencedEuLocations() {
        Set<Long> ids = new HashSet<>();
        for (EmsStationTariff tariff : tariffMapper.selectList(new LambdaQueryWrapper<>())) {
            if (references(tariff.getBuyMode(), tariff.getBuyLocationId())) {
                ids.add(tariff.getBuyLocationId());
            }
            if (references(tariff.getSellMode(), tariff.getSellLocationId())) {
                ids.add(tariff.getSellLocationId());
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return locationMapper.selectList(new LambdaQueryWrapper<EmsPriceLocation>()
                .eq(EmsPriceLocation::getMarket, "EU")
                .in(EmsPriceLocation::getId, ids));
    }

    private boolean references(String mode, Long locationId) {
        return EmsStationTariff.MARKET.equals(mode) && locationId != null;
    }
}
