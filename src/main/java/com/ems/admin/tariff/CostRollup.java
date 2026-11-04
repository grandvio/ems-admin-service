package com.ems.admin.tariff;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CostRollup {
    public record HourEnergy(LocalDateTime hourStart, BigDecimal importKwh, BigDecimal exportKwh) {
    }

    public record Prices(BigDecimal buyPrice, BigDecimal sellPrice) {
    }

    public record Result(List<String> buckets, List<BigDecimal> purchaseCost, List<BigDecimal> saleIncome,
                         List<BigDecimal> netCost, List<Integer> missingHours) {
    }

    private CostRollup() {
    }

    public static Result roll(String period, LocalDate date, List<HourEnergy> energy, Map<LocalDateTime, Prices> prices) {
        LocalDateTime start;
        LocalDateTime end;
        List<String> labels;
        if ("DAY".equals(period)) {
            labels = labels(24, 0);
            start = date.atStartOfDay();
            end = start.plusDays(1);
        } else if ("MONTH".equals(period)) {
            YearMonth month = YearMonth.from(date);
            labels = labels(month.lengthOfMonth(), 1);
            start = month.atDay(1).atStartOfDay();
            end = start.plusMonths(1);
        } else if ("YEAR".equals(period)) {
            labels = labels(12, 1);
            start = LocalDate.of(date.getYear(), 1, 1).atStartOfDay();
            end = start.plusYears(1);
        } else {
            throw new IllegalArgumentException(period);
        }
        Map<LocalDateTime, HourEnergy> byHour = new HashMap<>();
        for (HourEnergy row : energy) {
            byHour.put(row.hourStart(), row);
        }
        BigDecimal[] purchase = new BigDecimal[labels.size()];
        BigDecimal[] income = new BigDecimal[labels.size()];
        int[] missing = new int[labels.size()];
        boolean[] seen = new boolean[labels.size()];
        for (LocalDateTime hour = start; hour.isBefore(end); hour = hour.plusHours(1)) {
            int index = index(period, date, hour);
            if (index < 0) {
                continue;
            }
            Prices slot = prices.get(hour);
            if (slot == null || slot.buyPrice() == null || slot.sellPrice() == null) {
                missing[index]++;
                continue;
            }
            HourEnergy row = byHour.get(hour);
            BigDecimal imported = row == null || row.importKwh() == null ? BigDecimal.ZERO : row.importKwh();
            BigDecimal exported = row == null || row.exportKwh() == null ? BigDecimal.ZERO : row.exportKwh();
            BigDecimal hourPurchase = money(imported.multiply(slot.buyPrice()));
            BigDecimal hourIncome = money(exported.multiply(slot.sellPrice()));
            if (!seen[index]) {
                purchase[index] = hourPurchase;
                income[index] = hourIncome;
                seen[index] = true;
            } else {
                purchase[index] = purchase[index].add(hourPurchase);
                income[index] = income[index].add(hourIncome);
            }
        }
        List<BigDecimal> purchaseRows = new ArrayList<>();
        List<BigDecimal> incomeRows = new ArrayList<>();
        List<BigDecimal> netRows = new ArrayList<>();
        List<Integer> missingRows = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            purchaseRows.add(purchase[i]);
            incomeRows.add(income[i]);
            netRows.add(purchase[i] == null || income[i] == null ? null : purchase[i].subtract(income[i]));
            missingRows.add(missing[i]);
        }
        return new Result(labels, purchaseRows, incomeRows, netRows, missingRows);
    }

    private static int index(String period, LocalDate date, LocalDateTime hour) {
        return switch (period) {
            case "DAY" -> hour.toLocalDate().equals(date) ? hour.getHour() : -1;
            case "MONTH" -> YearMonth.from(hour).equals(YearMonth.from(date)) ? hour.getDayOfMonth() - 1 : -1;
            case "YEAR" -> hour.getYear() == date.getYear() ? hour.getMonthValue() - 1 : -1;
            default -> -1;
        };
    }

    private static List<String> labels(int count, int origin) {
        List<String> labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            labels.add(String.format("%02d", i + origin));
        }
        return labels;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
