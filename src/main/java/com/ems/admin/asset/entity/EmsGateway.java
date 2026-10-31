package com.ems.admin.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_gateway")
public class EmsGateway extends BaseEntity {
    private Long stationId;
    private String name;
    /** Sparkplug groupId */
    private String groupId;
    /** Sparkplug edgeNodeId */
    private String edgeNodeId;
    private String onlineStatus;
    private LocalDateTime lastBirthAt;
    private LocalDateTime lastDeathAt;
}
