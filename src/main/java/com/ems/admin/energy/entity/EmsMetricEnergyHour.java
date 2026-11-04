package com.ems.admin.energy.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_metric_energy_hour")
public class EmsMetricEnergyHour extends BaseEntity {
    private Long stationId;
    private Long deviceId;
    private String metricName;
    private LocalDateTime hourStart;
    private BigDecimal kwh;
}
