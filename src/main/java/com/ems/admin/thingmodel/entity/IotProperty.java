package com.ems.admin.thingmodel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_property")
public class IotProperty extends BaseEntity {
    private Long productId;
    /** 与 Sparkplug metricName、TDengine 标签 metricName 一致 */
    private String identifier;
    private String name;
    private String dataType;
    private String unit;
    private String accessMode;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String groupCode;
}
