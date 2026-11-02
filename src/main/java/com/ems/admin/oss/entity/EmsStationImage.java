package com.ems.admin.oss.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_station_image")
public class EmsStationImage extends BaseEntity {
    private Long stationId;
    private String objectKey;
    private Integer cover;
    private Integer sortNum;
}
