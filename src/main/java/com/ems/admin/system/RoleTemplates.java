package com.ems.admin.system;

import java.util.Set;

public final class RoleTemplates {
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String STATION_ADMIN = "STATION_ADMIN";
    public static final String OPERATOR = "OPERATOR";
    public static final String VIEWER = "VIEWER";

    public static final Set<String> RESERVED = Set.of(
            PLATFORM_ADMIN, TENANT_ADMIN, STATION_ADMIN, OPERATOR, VIEWER
    );

    private static final Set<String> READ_ASSET = Set.of(
            Perms.STATION_LIST, Perms.GATEWAY_LIST, Perms.DEVICE_LIST, Perms.METRIC_QUERY
    );

    public static Set<String> tenantAdmin() {
        return Set.of(
                Perms.USER_LIST, Perms.USER_ADD, Perms.USER_EDIT, Perms.USER_REMOVE,
                Perms.ROLE_LIST, Perms.ROLE_ADD, Perms.ROLE_EDIT, Perms.ROLE_REMOVE,
                Perms.STATION_LIST, Perms.STATION_ADD, Perms.STATION_EDIT, Perms.STATION_REMOVE,
                Perms.GATEWAY_LIST, Perms.GATEWAY_ADD, Perms.GATEWAY_EDIT, Perms.GATEWAY_REMOVE,
                Perms.DEVICE_LIST, Perms.DEVICE_ADD, Perms.DEVICE_EDIT, Perms.DEVICE_REMOVE, Perms.DEVICE_CONTROL,
                Perms.PRODUCT_LIST, Perms.PRODUCT_ADD, Perms.PRODUCT_EDIT, Perms.PRODUCT_REMOVE, Perms.PRODUCT_PUBLISH,
                Perms.PROPERTY_LIST, Perms.PROPERTY_ADD, Perms.PROPERTY_EDIT, Perms.PROPERTY_REMOVE,
                Perms.EVENT_LIST, Perms.EVENT_ADD, Perms.EVENT_EDIT, Perms.EVENT_REMOVE,
                Perms.SERVICE_LIST, Perms.SERVICE_ADD, Perms.SERVICE_EDIT, Perms.SERVICE_REMOVE,
                Perms.METRIC_QUERY
        );
    }

    public static Set<String> stationAdmin() {
        return Set.of(
                Perms.STATION_LIST, Perms.STATION_ADD, Perms.STATION_EDIT, Perms.STATION_REMOVE,
                Perms.GATEWAY_LIST, Perms.GATEWAY_ADD, Perms.GATEWAY_EDIT, Perms.GATEWAY_REMOVE,
                Perms.DEVICE_LIST, Perms.DEVICE_ADD, Perms.DEVICE_EDIT, Perms.DEVICE_REMOVE,
                Perms.PRODUCT_LIST, Perms.PROPERTY_LIST, Perms.EVENT_LIST, Perms.SERVICE_LIST,
                Perms.METRIC_QUERY
        );
    }

    public static Set<String> operator() {
        return Set.of(
                Perms.STATION_LIST, Perms.GATEWAY_LIST, Perms.DEVICE_LIST, Perms.DEVICE_CONTROL, Perms.METRIC_QUERY
        );
    }

    public static Set<String> viewer() {
        return READ_ASSET;
    }

    private RoleTemplates() {
    }
}
