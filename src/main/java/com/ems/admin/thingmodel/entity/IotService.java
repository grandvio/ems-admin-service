package com.ems.admin.thingmodel.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iot_service")
public class IotService extends BaseEntity {
    private Long productId;
    private String identifier;
    private String name;
    private String inputParams;
    private String description;
}
