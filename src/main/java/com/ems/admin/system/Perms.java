package com.ems.admin.system;

public final class Perms {
    public static final String TENANT_LIST = "system:tenant:list";
    public static final String TENANT_ADD = "system:tenant:add";
    public static final String TENANT_EDIT = "system:tenant:edit";
    public static final String TENANT_REMOVE = "system:tenant:remove";

    public static final String USER_LIST = "system:user:list";
    public static final String USER_ADD = "system:user:add";
    public static final String USER_EDIT = "system:user:edit";
    public static final String USER_REMOVE = "system:user:remove";

    public static final String ROLE_LIST = "system:role:list";
    public static final String ROLE_ADD = "system:role:add";
    public static final String ROLE_EDIT = "system:role:edit";
    public static final String ROLE_REMOVE = "system:role:remove";

    public static final String MENU_LIST = "system:menu:list";
    public static final String MENU_ADD = "system:menu:add";
    public static final String MENU_EDIT = "system:menu:edit";
    public static final String MENU_REMOVE = "system:menu:remove";

    public static final String STATION_LIST = "station:station:list";
    public static final String STATION_ADD = "station:station:add";
    public static final String STATION_EDIT = "station:station:edit";
    public static final String STATION_REMOVE = "station:station:remove";

    public static final String GATEWAY_LIST = "station:gateway:list";
    public static final String GATEWAY_ADD = "station:gateway:add";
    public static final String GATEWAY_EDIT = "station:gateway:edit";
    public static final String GATEWAY_REMOVE = "station:gateway:remove";

    public static final String DEVICE_LIST = "station:device:list";
    public static final String DEVICE_ADD = "station:device:add";
    public static final String DEVICE_EDIT = "station:device:edit";
    public static final String DEVICE_REMOVE = "station:device:remove";
    public static final String DEVICE_CONTROL = "station:device:control";

    public static final String PRODUCT_LIST = "thing:product:list";
    public static final String PRODUCT_ADD = "thing:product:add";
    public static final String PRODUCT_EDIT = "thing:product:edit";
    public static final String PRODUCT_REMOVE = "thing:product:remove";
    public static final String PRODUCT_PUBLISH = "thing:product:publish";

    public static final String PROPERTY_LIST = "thing:property:list";
    public static final String PROPERTY_ADD = "thing:property:add";
    public static final String PROPERTY_EDIT = "thing:property:edit";
    public static final String PROPERTY_REMOVE = "thing:property:remove";

    public static final String EVENT_LIST = "thing:event:list";
    public static final String EVENT_ADD = "thing:event:add";
    public static final String EVENT_EDIT = "thing:event:edit";
    public static final String EVENT_REMOVE = "thing:event:remove";

    public static final String SERVICE_LIST = "thing:service:list";
    public static final String SERVICE_ADD = "thing:service:add";
    public static final String SERVICE_EDIT = "thing:service:edit";
    public static final String SERVICE_REMOVE = "thing:service:remove";

    public static final String METRIC_QUERY = "data:metric:query";

    private Perms() {
    }
}
