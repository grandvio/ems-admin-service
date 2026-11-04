package com.ems.admin.energy;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyDeltasTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");

    @Test
    void firstReadingIsBaseline() {
        long at = epoch(2026, 11, 2, 1, 15);
        assertTrue(EnergyDeltas.fold(null, List.of(new EnergyDeltas.Reading(at, 10)), zone).isEmpty());
    }

    @Test
    void positiveDeltaGoesToLaterHour() {
        long at = epoch(2026, 11, 2, 1, 15);
        List<EnergyDeltas.Slice> slices = EnergyDeltas.fold(100d, List.of(new EnergyDeltas.Reading(at, 102.5)), zone);
        assertEquals(1, slices.size());
        assertEquals(2.5, slices.get(0).kwh(), 0.0001);
        assertEquals(LocalDateTime.of(2026, 11, 2, 1, 0), slices.get(0).hourStart());
    }

    @Test
    void negativeDeltaUsesCurrentRegister() {
        long at = epoch(2026, 11, 2, 3, 40);
        List<EnergyDeltas.Slice> slices = EnergyDeltas.fold(100d, List.of(new EnergyDeltas.Reading(at, 10)), zone);
        assertEquals(10, slices.get(0).kwh(), 0.0001);
        assertEquals(LocalDateTime.of(2026, 11, 2, 3, 0), slices.get(0).hourStart());
    }

    @Test
    void sumsSlicesInTheSameHour() {
        long first = epoch(2026, 11, 2, 1, 10);
        long second = epoch(2026, 11, 2, 1, 40);
        List<EnergyDeltas.Slice> slices = EnergyDeltas.fold(0d, List.of(
                new EnergyDeltas.Reading(first, 1),
                new EnergyDeltas.Reading(second, 2.5)
        ), zone);
        assertEquals(2.5, EnergyDeltas.sumByHour(slices).get(LocalDateTime.of(2026, 11, 2, 1, 0)), 0.0001);
    }

    private long epoch(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli();
    }
}
