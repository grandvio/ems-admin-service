package com.ems.admin.energy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_energy_source")
public class EmsEnergySource extends BaseEntity {
    private Long stationId;
    private String seriesCode;
    private Long deviceId;
    private String metricName;
}
