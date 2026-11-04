package com.ems.admin.tariff;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PriceHoursTest {
    @Test
    void averagesQuarterHoursAndConvertsToPerKwh() {
        List<PriceHours.Interval> intervals = List.of(
                interval("2026-11-03T23:00:00Z", 80),
                interval("2026-11-03T23:15:00Z", 100),
                interval("2026-11-03T23:30:00Z", -20),
                interval("2026-11-03T23:45:00Z", 40)
        );
        BigDecimal price = PriceHours.averagePerKwh(intervals, LocalDateTime.of(2026, 11, 4, 0, 0), ZoneId.of("Europe/Berlin"));
        assertEquals(new BigDecimal("0.050000"), price);
    }

    @Test
    void averagesFiveMinutePricesIntoTheHour() {
        Instant start = Instant.parse("2026-11-03T23:00:00Z");
        List<PriceHours.Interval> intervals = new ArrayList<>();
        for (int price = 10; price <= 120; price += 10) {
            intervals.add(new PriceHours.Interval(start, 5, BigDecimal.valueOf(price)));
            start = start.plus(Duration.ofMinutes(5));
        }
        BigDecimal average = PriceHours.averagePerKwh(intervals, LocalDateTime.of(2026, 11, 4, 0, 0), ZoneId.of("Europe/Berlin"));
        assertEquals(new BigDecimal("0.065000"), average);
    }

    @Test
    void marketAdderUsesPerKwhPrice() {
        BigDecimal settled = PriceHours.settle("MARKET", null, new BigDecimal("0.080000"), new BigDecimal("0.12"));
        assertEquals(new BigDecimal("0.200000"), settled);
        assertNull(PriceHours.settle("MARKET", null, null, new BigDecimal("0.12")));
    }

    private PriceHours.Interval interval(String start, int eurPerMwh) {
        return new PriceHours.Interval(Instant.parse(start), 15, BigDecimal.valueOf(eurPerMwh));
    }
}
