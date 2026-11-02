package com.ems.admin.thingmodel.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 可跨环境搬运的物模型文件。不含数据库 id、租户和发布状态。
 */
@Data
public class ThingModelFile {
    public static final int SPEC_VERSION = 1;

    private Integer specVersion = SPEC_VERSION;
    private ThingDtos.ProductRequest product;
    private List<ThingDtos.PropertyRequest> properties = new ArrayList<>();
    private List<ThingDtos.EventRequest> events = new ArrayList<>();
    private List<ThingDtos.ServiceRequest> services = new ArrayList<>();
}
