package com.ems.admin.energy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

public final class EnergyDtos {
    private EnergyDtos() {
    }

    @Data
    public static class Chart {
        private String period;
        private String unit;
        private List<String> buckets;
        private Series series;
    }

    @Data
    public static class Series {
        private List<BigDecimal> charge;
        private List<BigDecimal> discharge;
        private List<BigDecimal> generation;
        private List<BigDecimal> consumption;
        private List<BigDecimal> importEnergy;
        private List<BigDecimal> exportEnergy;
    }

    public record ReplaceSourcesRequest(@NotNull @Valid List<SourceInput> sources) {
    }

    public record SourceInput(
            @NotNull Long deviceId,
            @NotBlank @Size(max = 128) String metricName
    ) {
    }

    @Data
    public static class SourceBoard {
        private List<SourceItem> charge;
        private List<SourceItem> discharge;
        private List<SourceItem> generation;
        private List<SourceItem> consumption;
        private List<SourceItem> importEnergy;
        private List<SourceItem> exportEnergy;
    }

    @Data
    public static class SourceItem {
        private Long id;
        private Long deviceId;
        private String deviceName;
        private String metricName;
        private String metricLabel;
    }
}
