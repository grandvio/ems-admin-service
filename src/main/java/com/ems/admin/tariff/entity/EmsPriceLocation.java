package com.ems.admin.tariff.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_price_location")
public class EmsPriceLocation extends BaseEntity {
    private String market;
    private String code;
    private String name;
    private String currency;
    private String timezone;
    private String isoCode;
    private String externalRef;
}
