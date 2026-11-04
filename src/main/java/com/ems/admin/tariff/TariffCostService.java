package com.ems.admin.tariff;

import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.energy.StationEnergyService;
import com.ems.admin.tariff.entity.EmsStationTariff;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Map;

@Service
public class TariffCostService {
    private final TariffService tariffService;
    private final StationEnergyService stationEnergyService;

    public TariffCostService(TariffService tariffService, StationEnergyService stationEnergyService) {
        this.tariffService = tariffService;
        this.stationEnergyService = stationEnergyService;
    }

    public TariffDtos.CostChart cost(Long stationId, String period, LocalDate date) {
        EmsStation station = tariffService.requireStation(stationId);
        EmsStationTariff tariff = tariffService.requireTariff(stationId);
        if (date == null) {
            throw BizException.badRequest("请指定日期");
        }
        String normalized = period == null ? "" : period.trim().toUpperCase();
        if (!"DAY".equals(normalized) && !"MONTH".equals(normalized) && !"YEAR".equals(normalized)) {
            throw BizException.badRequest("周期只能是 DAY、MONTH 或 YEAR");
        }
        LocalDateTime start = start(normalized, date);
        LocalDateTime end = end(normalized, date, start);
        Map<LocalDateTime, double[]> gate = stationEnergyService.hourlyImportExport(stationId, start, end);
        java.util.List<CostRollup.HourEnergy> energy = new ArrayList<>();
        gate.forEach((hour, pair) -> energy.add(new CostRollup.HourEnergy(hour, BigDecimal.valueOf(pair[0]), BigDecimal.valueOf(pair[1]))));
        CostRollup.Result result = CostRollup.roll(normalized, date, energy, tariffService.priceSlots(station, tariff, start, end));
        TariffDtos.CostChart chart = new TariffDtos.CostChart();
        chart.setPeriod(normalized);
        chart.setCurrency(tariff.getCurrency());
        chart.setBuckets(result.buckets());
        chart.setPurchaseCost(result.purchaseCost());
        chart.setSaleIncome(result.saleIncome());
        chart.setNetCost(result.netCost());
        chart.setMissingHours(result.missingHours());
        return chart;
    }

    private LocalDateTime start(String period, LocalDate date) {
        return switch (period) {
            case "MONTH" -> YearMonth.from(date).atDay(1).atStartOfDay();
            case "YEAR" -> LocalDate.of(date.getYear(), 1, 1).atStartOfDay();
            default -> date.atStartOfDay();
        };
    }

    private LocalDateTime end(String period, LocalDate date, LocalDateTime start) {
        return switch (period) {
            case "MONTH" -> start.plusMonths(1);
            case "YEAR" -> start.plusYears(1);
            default -> start.plusDays(1);
        };
    }
}
