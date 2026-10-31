package com.ems.admin.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_device")
public class EmsDevice extends BaseEntity {
    private Long stationId;
    private Long gatewayId;
    private String name;
    /** Sparkplug deviceId */
    private String deviceCode;
    private Long productId;
    private Integer modelVersion;
    private String sn;
    private String status;
}
