package com.ems.admin.tariff;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CostRollupTest {
    @Test
    void fixedPricesBillImportAndExport() {
        LocalDateTime hour = LocalDateTime.of(2026, 11, 4, 1, 0);
        CostRollup.Result result = CostRollup.roll("DAY", LocalDate.of(2026, 11, 4),
                List.of(new CostRollup.HourEnergy(hour, new BigDecimal("10"), new BigDecimal("4"))),
                Map.of(hour, new CostRollup.Prices(new BigDecimal("0.25"), new BigDecimal("0.08"))));
        assertEquals(new BigDecimal("2.500000"), result.purchaseCost().get(1));
        assertEquals(new BigDecimal("0.320000"), result.saleIncome().get(1));
        assertEquals(new BigDecimal("2.180000"), result.netCost().get(1));
        assertNull(result.purchaseCost().get(0));
        assertEquals(1, result.missingHours().get(0));
    }

    @Test
    void monthSkipsHoursWithoutPrice() {
        LocalDateTime priced = LocalDateTime.of(2026, 11, 2, 1, 0);
        LocalDateTime missing = LocalDateTime.of(2026, 11, 2, 2, 0);
        CostRollup.Result result = CostRollup.roll("MONTH", LocalDate.of(2026, 11, 2),
                List.of(
                        new CostRollup.HourEnergy(priced, new BigDecimal("10"), BigDecimal.ZERO),
                        new CostRollup.HourEnergy(missing, new BigDecimal("10"), BigDecimal.ZERO)
                ),
                Map.of(priced, new CostRollup.Prices(new BigDecimal("0.20"), new BigDecimal("0.10"))));
        assertEquals(new BigDecimal("2.000000"), result.purchaseCost().get(1));
        assertEquals(23, result.missingHours().get(1));
    }
}
