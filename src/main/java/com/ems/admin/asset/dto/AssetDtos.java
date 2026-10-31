package com.ems.admin.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class AssetDtos {
    private AssetDtos() {
    }

    public record StationRequest(
            @NotBlank @Size(max = 32) String code,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Pattern(regexp = "PV|STORAGE|PV_STORAGE|CHARGE") String stationType,
            @Size(max = 255) String address,
            BigDecimal longitude,
            BigDecimal latitude,
            @Size(max = 64) String timezone,
            BigDecimal capacityKw,
            BigDecimal storageKwh,
            LocalDate gridConnectedDate,
            @NotBlank @Pattern(regexp = "BUILDING|RUNNING|STOPPED") String status
    ) {
    }

    @Data
    public static class StationVO {
        private Long id;
        private Long tenantId;
        private String code;
        private String name;
        private String stationType;
        private String address;
        private BigDecimal longitude;
        private BigDecimal latitude;
        private String timezone;
        private BigDecimal capacityKw;
        private BigDecimal storageKwh;
        private LocalDate gridConnectedDate;
        private String status;
        private LocalDateTime createdAt;
    }

    public record GatewayRequest(
            @NotNull Long stationId,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 64) String groupId,
            @NotBlank @Size(max = 64) String edgeNodeId,
            @Pattern(regexp = "ONLINE|OFFLINE") String onlineStatus
    ) {
    }

    @Data
    public static class GatewayVO {
        private Long id;
        private Long stationId;
        private String name;
        private String groupId;
        private String edgeNodeId;
        private String onlineStatus;
        private LocalDateTime lastBirthAt;
        private LocalDateTime lastDeathAt;
        private LocalDateTime createdAt;
    }

    public record DeviceRequest(
            @NotNull Long gatewayId,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Size(max = 64) String deviceCode,
            @Size(max = 64) String sn,
            @Pattern(regexp = "INACTIVE|ONLINE|OFFLINE|ALARM") String status
    ) {
    }

    public record BindProductRequest(@NotNull Long productId) {
    }

    @Data
    public static class DeviceVO {
        private Long id;
        private Long stationId;
        private Long gatewayId;
        private String name;
        private String deviceCode;
        private Long productId;
        private Integer modelVersion;
        private String sn;
        private String status;
        private LocalDateTime createdAt;
    }
}
