package com.ems.admin.tariff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class PriceHours {
    public static final BigDecimal MWH_PER_KWH = new BigDecimal("1000");

    public record Interval(Instant start, int minutes, BigDecimal pricePerMwh) {
    }

    private PriceHours() {
    }

    public static BigDecimal averagePerKwh(List<Interval> intervals, LocalDateTime hourStart, ZoneId zone) {
        ZonedDateTime hour = hourStart.atZone(zone);
        Instant from = hour.toInstant();
        Instant to = hour.plusHours(1).toInstant();
        List<BigDecimal> matched = new ArrayList<>();
        for (Interval interval : intervals) {
            Instant end = interval.start().plus(Duration.ofMinutes(interval.minutes()));
            if (interval.start().isBefore(to) && end.isAfter(from)) {
                matched.add(interval.pricePerMwh());
            }
        }
        if (matched.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal price : matched) {
            sum = sum.add(price);
        }
        return sum.divide(BigDecimal.valueOf(matched.size()), 6, RoundingMode.HALF_UP)
                .divide(MWH_PER_KWH, 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal settle(String mode, BigDecimal fixedPrice, BigDecimal marketPerKwh, BigDecimal adder) {
        if ("FIXED".equals(mode)) {
            return fixedPrice;
        }
        if (marketPerKwh == null) {
            return null;
        }
        return marketPerKwh.add(adder == null ? BigDecimal.ZERO : adder);
    }
}
