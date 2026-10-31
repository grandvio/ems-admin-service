package com.ems.admin.thingmodel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_product")
public class IotProduct extends BaseEntity {
    private String code;
    private String name;
    private String status;
    private Integer modelVersion;
    private String description;
}
