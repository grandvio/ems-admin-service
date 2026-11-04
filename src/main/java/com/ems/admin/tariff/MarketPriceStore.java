package com.ems.admin.tariff;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.tariff.entity.EmsMarketPrice;
import com.ems.admin.tariff.mapper.EmsMarketPriceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class MarketPriceStore {
    private final EmsMarketPriceMapper priceMapper;

    public MarketPriceStore(EmsMarketPriceMapper priceMapper) {
        this.priceMapper = priceMapper;
    }

    @Transactional
    public void upsert(Long locationId, String currency, String product, List<EntsoePriceParser.Point> points) {
        for (EntsoePriceParser.Point point : points) {
            LocalDateTime start = LocalDateTime.ofInstant(point.start(), ZoneOffset.UTC);
            String direction = PriceDirections.normalize(point.direction());
            EmsMarketPrice existing = priceMapper.selectOne(new LambdaQueryWrapper<EmsMarketPrice>()
                    .eq(EmsMarketPrice::getLocationId, locationId)
                    .eq(EmsMarketPrice::getProduct, product)
                    .eq(EmsMarketPrice::getDirection, direction)
                    .eq(EmsMarketPrice::getIntervalStart, start));
            if (existing == null) {
                EmsMarketPrice row = new EmsMarketPrice();
                row.setTenantId(0L);
                row.setLocationId(locationId);
                row.setProduct(product);
                row.setDirection(direction);
                row.setIntervalStart(start);
                row.setIntervalMinutes(point.minutes());
                row.setPricePerMwh(point.pricePerMwh().setScale(6, RoundingMode.HALF_UP));
                row.setCurrency(currency);
                priceMapper.insert(row);
            } else {
                existing.setIntervalMinutes(point.minutes());
                existing.setPricePerMwh(point.pricePerMwh().setScale(6, RoundingMode.HALF_UP));
                existing.setCurrency(currency);
                priceMapper.updateById(existing);
            }
        }
    }
}
