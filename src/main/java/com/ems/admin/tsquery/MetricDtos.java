package com.ems.admin.tsquery;

import lombok.Data;

import java.util.List;

public final class MetricDtos {
    private MetricDtos() {
    }

    @Data
    public static class Point {
        private Long ts;
        private Object value;
        private Integer quality;
    }

    @Data
    public static class LatestItem {
        private String identifier;
        private String name;
        private String unit;
        private String dataType;
        private Long ts;
        private Object value;
        private Integer quality;
    }

    @Data
    public static class History {
        private String identifier;
        private String name;
        private String unit;
        private String dataType;
        private boolean truncated;
        private List<Point> points;
    }
}
