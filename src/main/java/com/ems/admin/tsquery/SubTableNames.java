package com.ems.admin.tsquery;

import com.ems.admin.common.exception.BizException;

/**
 * 子表名规则与 sparkplug {@code TsdbService#buildSubTable} 保持一致。
 */
public final class SubTableNames {
    private SubTableNames() {
    }

    public static String qualified(String database, String groupId, String edgeNodeId, String deviceId, String metricName) {
        if (database == null || !database.matches("[A-Za-z0-9_]+")) {
            throw BizException.badRequest("时序库名不合法");
        }
        String raw = "sub_" + empty(groupId) + "_" + empty(edgeNodeId) + "_" + empty(deviceId) + "_" + empty(metricName);
        String safe = raw.replaceAll("[^A-Za-z0-9_]", "_");
        return database + "." + safe;
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
