package com.ems.admin.tariff.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_station_tariff")
public class EmsStationTariff extends BaseEntity {
    public static final String FIXED = "FIXED";
    public static final String MARKET = "MARKET";

    private Long stationId;
    private String currency;
    private String buyMode;
    private String buyProduct = EmsMarketPrice.DAY_AHEAD;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal buyFixedPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long buyLocationId;
    private BigDecimal buyAdder;
    private String sellMode;
    private String sellProduct = EmsMarketPrice.DAY_AHEAD;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal sellFixedPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sellLocationId;
    private BigDecimal sellAdder;
}
