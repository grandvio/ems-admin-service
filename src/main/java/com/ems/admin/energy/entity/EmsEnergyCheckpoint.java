package com.ems.admin.energy.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_energy_checkpoint")
public class EmsEnergyCheckpoint extends BaseEntity {
    private Long deviceId;
    private String metricName;
    private Long lastEpochMs;
    @TableField("register_value")
    private BigDecimal lastValue;
}
