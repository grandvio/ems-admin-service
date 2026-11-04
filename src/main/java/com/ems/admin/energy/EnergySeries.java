package com.ems.admin.energy;

import java.util.List;
import java.util.Map;

public final class EnergySeries {
    public static final String CHARGE = "charge";
    public static final String DISCHARGE = "discharge";
    public static final String GENERATION = "generation";
    public static final String CONSUMPTION = "consumption";
    public static final String IMPORT = "importEnergy";
    public static final String EXPORT = "exportEnergy";
    public static final List<String> ALL = List.of(CHARGE, DISCHARGE, GENERATION, CONSUMPTION, IMPORT, EXPORT);
    private static final Map<String, String> LABELS = Map.of(
            CHARGE, "充电量",
            DISCHARGE, "放电量",
            GENERATION, "发电量",
            CONSUMPTION, "用电量",
            IMPORT, "买电量",
            EXPORT, "卖电量"
    );

    private EnergySeries() {
    }

    public static boolean contains(String series) {
        return LABELS.containsKey(series);
    }

    public static String label(String series) {
        return LABELS.getOrDefault(series, series);
    }

    public static void finishConsumption(double[] charge, double[] discharge, double[] generation,
                                         double[] consumption, double[] importEnergy, double[] exportEnergy,
                                         boolean measured) {
        if (measured) {
            return;
        }
        for (int i = 0; i < consumption.length; i++) {
            consumption[i] = generation[i] + importEnergy[i] + discharge[i] - charge[i] - exportEnergy[i];
        }
    }
}
