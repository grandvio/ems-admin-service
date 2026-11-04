package com.ems.admin.tariff.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_market_price")
public class EmsMarketPrice extends BaseEntity {
    public static final String DAY_AHEAD = "DAY_AHEAD";
    public static final String REAL_TIME = "REAL_TIME";
    public static final String SINGLE = "SINGLE";
    public static final String DOWN = "DOWN";
    public static final String UP = "UP";

    private Long locationId;
    private String product;
    private String direction = SINGLE;
    private LocalDateTime intervalStart;
    private Integer intervalMinutes;
    private BigDecimal pricePerMwh;
    private String currency;
}
