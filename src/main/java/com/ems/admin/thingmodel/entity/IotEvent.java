package com.ems.admin.thingmodel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_event")
public class IotEvent extends BaseEntity {
    private Long productId;
    private String identifier;
    private String name;
    private String level;
    private String description;
}
