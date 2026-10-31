package com.ems.admin.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ems_station")
public class EmsStation extends BaseEntity {
    private String code;
    private String name;
    private String stationType;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String timezone;
    private BigDecimal capacityKw;
    private BigDecimal storageKwh;
    private LocalDate gridConnectedDate;
    private String status;
}
