package com.ems.admin.energy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 累计电能差值。第一个读数只作为基线，之后每次用后一个点减去前一个点，归入后一个点所在的小时。
 */
public final class EnergyDeltas {
    private EnergyDeltas() {
    }

    public record Reading(long epochMilli, double value) {
    }

    public record Slice(LocalDateTime hourStart, double kwh) {
    }

    public static List<Slice> fold(Double previous, List<Reading> readings, ZoneId zone) {
        List<Slice> slices = new ArrayList<>();
        Double last = previous;
        for (Reading reading : readings) {
            if (last != null) {
                double delta = reading.value() - last;
                if (delta < 0) {
                    delta = Math.max(reading.value(), 0);
                }
                if (delta > 0) {
                    LocalDateTime hour = LocalDateTime.ofInstant(Instant.ofEpochMilli(reading.epochMilli()), zone)
                            .truncatedTo(ChronoUnit.HOURS);
                    slices.add(new Slice(hour, delta));
                }
            }
            last = reading.value();
        }
        return slices;
    }

    public static Map<LocalDateTime, Double> sumByHour(List<Slice> slices) {
        Map<LocalDateTime, Double> totals = new LinkedHashMap<>();
        for (Slice slice : slices) {
            totals.merge(slice.hourStart(), slice.kwh(), Double::sum);
        }
        return totals;
    }
}
