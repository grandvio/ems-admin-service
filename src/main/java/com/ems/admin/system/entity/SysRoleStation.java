package com.ems.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_station")
public class SysRoleStation extends BaseEntity {
    private Long roleId;
    private Long stationId;
}
