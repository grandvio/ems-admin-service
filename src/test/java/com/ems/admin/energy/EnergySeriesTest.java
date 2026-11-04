package com.ems.admin.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergySeriesTest {
    @Test
    void consumptionUsesBalanceWhenUnmeasured() {
        double[] charge = {1};
        double[] discharge = {4};
        double[] generation = {10};
        double[] consumption = {0};
        double[] importEnergy = {3};
        double[] exportEnergy = {2};
        EnergySeries.finishConsumption(charge, discharge, generation, consumption, importEnergy, exportEnergy, false);
        assertEquals(14, consumption[0], 0.0001);
    }

    @Test
    void measuredConsumptionStays() {
        double[] consumption = {9};
        EnergySeries.finishConsumption(new double[]{1}, new double[]{1}, new double[]{1}, consumption,
                new double[]{1}, new double[]{1}, true);
        assertEquals(9, consumption[0], 0.0001);
    }
}
