package com.ems.admin.energy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.asset.service.StationGuard;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.energy.entity.EmsEnergySource;
import com.ems.admin.energy.entity.EmsMetricEnergyHour;
import com.ems.admin.energy.mapper.EmsMetricEnergyHourMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StationEnergyService {
    private final EmsStationMapper stationMapper;
    private final EmsMetricEnergyHourMapper hourMapper;
    private final EnergySourceService energySourceService;
    private final StationGuard stationGuard;

    public StationEnergyService(EmsStationMapper stationMapper, EmsMetricEnergyHourMapper hourMapper,
                                EnergySourceService energySourceService, StationGuard stationGuard) {
        this.stationMapper = stationMapper;
        this.hourMapper = hourMapper;
        this.energySourceService = energySourceService;
        this.stationGuard = stationGuard;
    }

    public EnergyDtos.Chart chart(Long stationId, String period, LocalDate date) {
        stationGuard.check(stationId);
        EmsStation station = stationMapper.selectById(stationId);
        if (station == null) {
            throw BizException.notFound("电站不存在");
        }
        String normalized = period == null ? "" : period.trim().toUpperCase();
        return switch (normalized) {
            case "DAY" -> periodChart(station, "DAY", requireDate(date), Buckets.day(requireDate(date)));
            case "MONTH" -> periodChart(station, "MONTH", requireDate(date), Buckets.month(requireDate(date)));
            case "YEAR" -> periodChart(station, "YEAR", requireDate(date), Buckets.year(requireDate(date)));
            case "TOTAL" -> totalChart(station);
            default -> throw BizException.badRequest("周期只能是 DAY、MONTH、YEAR 或 TOTAL");
        };
    }

    private EnergyDtos.Chart periodChart(EmsStation station, String period, LocalDate date, BucketPlan plan) {
        List<EmsEnergySource> sources = energySourceService.sourcesOf(station.getId());
        List<EmsMetricEnergyHour> hours = hours(station.getId(), sources, plan.start(), plan.end());
        return toChart(period, plan.labels(), sources, hours, hour -> plan.index(hour.getHourStart()));
    }

    private EnergyDtos.Chart totalChart(EmsStation station) {
        List<EmsEnergySource> sources = energySourceService.sourcesOf(station.getId());
        List<Long> deviceIds = sources.stream().map(EmsEnergySource::getDeviceId).distinct().toList();
        List<EmsMetricEnergyHour> hours = deviceIds.isEmpty() ? List.of() : hourMapper.selectList(new LambdaQueryWrapper<EmsMetricEnergyHour>()
                .eq(EmsMetricEnergyHour::getStationId, station.getId())
                .in(EmsMetricEnergyHour::getDeviceId, deviceIds)
                .orderByAsc(EmsMetricEnergyHour::getHourStart));
        if (hours.isEmpty()) {
            return toChart("TOTAL", List.of(), sources, List.of(), hour -> -1);
        }
        int minYear = hours.get(0).getHourStart().getYear();
        int maxYear = hours.get(hours.size() - 1).getHourStart().getYear();
        List<String> labels = new ArrayList<>();
        for (int year = minYear; year <= maxYear; year++) {
            labels.add(Integer.toString(year));
        }
        return toChart("TOTAL", labels, sources, hours, hour -> hour.getHourStart().getYear() - minYear);
    }

    private EnergyDtos.Chart toChart(String period, List<String> labels, List<EmsEnergySource> sources,
                                     List<EmsMetricEnergyHour> hours, Function<EmsMetricEnergyHour, Integer> indexOf) {
        Map<String, double[]> values = new java.util.LinkedHashMap<>();
        for (String series : EnergySeries.ALL) {
            values.put(series, new double[labels.size()]);
        }
        boolean measuredConsumption = sources.stream().anyMatch(source -> EnergySeries.CONSUMPTION.equals(source.getSeriesCode()));
        Map<String, String> seriesByMetric = sources.stream().collect(Collectors.toMap(
                source -> source.getDeviceId() + "\0" + source.getMetricName(),
                EmsEnergySource::getSeriesCode,
                (left, right) -> left));
        for (EmsMetricEnergyHour hour : hours) {
            Integer index = indexOf.apply(hour);
            if (index == null || index < 0 || index >= labels.size()) {
                continue;
            }
            String series = seriesByMetric.get(hour.getDeviceId() + "\0" + hour.getMetricName());
            double[] bucket = values.get(series);
            if (bucket == null) {
                continue;
            }
            bucket[index] += hour.getKwh() == null ? 0 : hour.getKwh().doubleValue();
        }
        EnergySeries.finishConsumption(values.get(EnergySeries.CHARGE), values.get(EnergySeries.DISCHARGE),
                values.get(EnergySeries.GENERATION), values.get(EnergySeries.CONSUMPTION),
                values.get(EnergySeries.IMPORT), values.get(EnergySeries.EXPORT), measuredConsumption);
        EnergyDtos.Series series = new EnergyDtos.Series();
        series.setCharge(decimals(values.get(EnergySeries.CHARGE)));
        series.setDischarge(decimals(values.get(EnergySeries.DISCHARGE)));
        series.setGeneration(decimals(values.get(EnergySeries.GENERATION)));
        series.setConsumption(decimals(values.get(EnergySeries.CONSUMPTION)));
        series.setImportEnergy(decimals(values.get(EnergySeries.IMPORT)));
        series.setExportEnergy(decimals(values.get(EnergySeries.EXPORT)));
        EnergyDtos.Chart chart = new EnergyDtos.Chart();
        chart.setPeriod(period);
        chart.setUnit("kWh");
        chart.setBuckets(labels);
        chart.setSeries(series);
        return chart;
    }

    private List<EmsMetricEnergyHour> hours(Long stationId, List<EmsEnergySource> sources, LocalDateTime start, LocalDateTime end) {
        List<Long> deviceIds = sources.stream().map(EmsEnergySource::getDeviceId).distinct().toList();
        if (deviceIds.isEmpty()) {
            return List.of();
        }
        return hourMapper.selectList(new LambdaQueryWrapper<EmsMetricEnergyHour>()
                .eq(EmsMetricEnergyHour::getStationId, stationId)
                .in(EmsMetricEnergyHour::getDeviceId, deviceIds)
                .ge(EmsMetricEnergyHour::getHourStart, start)
                .lt(EmsMetricEnergyHour::getHourStart, end));
    }

    public java.util.Map<LocalDateTime, double[]> hourlyImportExport(Long stationId, LocalDateTime start, LocalDateTime end) {
        List<EmsEnergySource> sources = energySourceService.sourcesOf(stationId);
        java.util.Map<String, String> seriesByMetric = sources.stream().collect(Collectors.toMap(
                source -> source.getDeviceId() + "\0" + source.getMetricName(),
                EmsEnergySource::getSeriesCode,
                (left, right) -> left));
        java.util.Map<LocalDateTime, double[]> rows = new java.util.HashMap<>();
        for (EmsMetricEnergyHour hour : hours(stationId, sources, start, end)) {
            String series = seriesByMetric.get(hour.getDeviceId() + "\0" + hour.getMetricName());
            if (!EnergySeries.IMPORT.equals(series) && !EnergySeries.EXPORT.equals(series)) {
                continue;
            }
            double[] pair = rows.computeIfAbsent(hour.getHourStart(), key -> new double[2]);
            double kwh = hour.getKwh() == null ? 0 : hour.getKwh().doubleValue();
            if (EnergySeries.IMPORT.equals(series)) {
                pair[0] += kwh;
            } else {
                pair[1] += kwh;
            }
        }
        return rows;
    }

    private LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw BizException.badRequest("请指定日期");
        }
        return date;
    }

    private List<BigDecimal> decimals(double[] values) {
        List<BigDecimal> rows = new ArrayList<>(values.length);
        for (double value : values) {
            rows.add(BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP));
        }
        return rows;
    }

    private record BucketPlan(List<String> labels, LocalDateTime start, LocalDateTime end,
                              Function<LocalDateTime, Integer> indexOf) {
        int index(LocalDateTime hourStart) {
            return indexOf.apply(hourStart);
        }
    }

    private static final class Buckets {
        private Buckets() {
        }

        static BucketPlan day(LocalDate date) {
            List<String> labels = new ArrayList<>(24);
            for (int hour = 0; hour < 24; hour++) {
                labels.add(String.format("%02d", hour));
            }
            LocalDateTime start = date.atStartOfDay();
            return new BucketPlan(labels, start, start.plusDays(1), hour -> hour.toLocalDate().equals(date) ? hour.getHour() : -1);
        }

        static BucketPlan month(LocalDate date) {
            YearMonth month = YearMonth.from(date);
            int length = month.lengthOfMonth();
            List<String> labels = new ArrayList<>(length);
            for (int day = 1; day <= length; day++) {
                labels.add(String.format("%02d", day));
            }
            LocalDateTime start = month.atDay(1).atStartOfDay();
            return new BucketPlan(labels, start, start.plusMonths(1),
                    hour -> YearMonth.from(hour).equals(month) ? hour.getDayOfMonth() - 1 : -1);
        }

        static BucketPlan year(LocalDate date) {
            List<String> labels = new ArrayList<>(12);
            for (int month = 1; month <= 12; month++) {
                labels.add(String.format("%02d", month));
            }
            LocalDateTime start = LocalDate.of(date.getYear(), 1, 1).atStartOfDay();
            return new BucketPlan(labels, start, start.plusYears(1),
                    hour -> hour.getYear() == date.getYear() ? hour.getMonthValue() - 1 : -1);
        }
    }
}
