package com.ems.admin.tariff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

public final class TariffDtos {
    private TariffDtos() {
    }

    @Data
    public static class LocationVO {
        private Long id;
        private String market;
        private String code;
        private String name;
        private String currency;
        private String timezone;
    }

    public record TariffRequest(
            @NotBlank @Pattern(regexp = "EUR|USD") String currency,
            @NotBlank @Pattern(regexp = "FIXED|MARKET") String buyMode,
            BigDecimal buyFixedPrice,
            Long buyLocationId,
            BigDecimal buyAdder,
            @NotBlank @Pattern(regexp = "FIXED|MARKET") String sellMode,
            BigDecimal sellFixedPrice,
            Long sellLocationId,
            BigDecimal sellAdder,
            String buyProduct,
            String sellProduct
    ) {
    }

    @Data
    public static class TariffVO {
        private String currency;
        private String buyMode;
        private String buyProduct;
        private BigDecimal buyFixedPrice;
        private Long buyLocationId;
        private String buyLocationCode;
        private String buyLocationName;
        private BigDecimal buyAdder;
        private String sellMode;
        private String sellProduct;
        private BigDecimal sellFixedPrice;
        private Long sellLocationId;
        private String sellLocationCode;
        private String sellLocationName;
        private BigDecimal sellAdder;
    }

    @Data
    public static class PriceChart {
        private String currency;
        private String unit;
        private List<String> buckets;
        private List<BigDecimal> dayAheadPrice;
        private List<BigDecimal> realTimePrice;
        private List<BigDecimal> marketBuyPrice;
        private List<BigDecimal> marketSellPrice;
        private List<BigDecimal> buyPrice;
        private List<BigDecimal> sellPrice;
    }

    @Data
    public static class CostChart {
        private String period;
        private String currency;
        private List<String> buckets;
        private List<BigDecimal> purchaseCost;
        private List<BigDecimal> saleIncome;
        private List<BigDecimal> netCost;
        private List<Integer> missingHours;
    }
}
