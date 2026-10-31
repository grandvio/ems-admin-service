package com.ems.admin.thingmodel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ThingDtos {
    private ThingDtos() {
    }

    public record ProductRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,32}") String code,
            @NotBlank @Size(max = 64) String name,
            @Size(max = 500) String description
    ) {
    }

    @Data
    public static class ProductVO {
        private Long id;
        private Long tenantId;
        private String code;
        private String name;
        private String status;
        private Integer modelVersion;
        private String description;
        private LocalDateTime createdAt;
    }

    public record PropertyRequest(
            @NotBlank @Size(max = 128) String identifier,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Pattern(regexp = "float|int|bool|string") String dataType,
            @Size(max = 32) String unit,
            @NotBlank @Pattern(regexp = "r|rw") String accessMode,
            BigDecimal minValue,
            BigDecimal maxValue,
            @NotBlank @Pattern(regexp = "TELEMETRY|STATUS|SETPOINT") String groupCode
    ) {
    }

    @Data
    public static class PropertyVO {
        private Long id;
        private Long productId;
        private String identifier;
        private String name;
        private String dataType;
        private String unit;
        private String accessMode;
        private BigDecimal minValue;
        private BigDecimal maxValue;
        private String groupCode;
    }

    public record EventRequest(
            @NotBlank @Size(max = 128) String identifier,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Pattern(regexp = "INFO|WARN|CRITICAL") String level,
            @Size(max = 500) String description
    ) {
    }

    @Data
    public static class EventVO {
        private Long id;
        private Long productId;
        private String identifier;
        private String name;
        private String level;
        private String description;
    }

    public record ServiceRequest(
            @NotBlank @Size(max = 128) String identifier,
            @NotBlank @Size(max = 64) String name,
            @Size(max = 2000) String inputParams,
            @Size(max = 500) String description
    ) {
    }

    @Data
    public static class ServiceVO {
        private Long id;
        private Long productId;
        private String identifier;
        private String name;
        private String inputParams;
        private String description;
    }
}
