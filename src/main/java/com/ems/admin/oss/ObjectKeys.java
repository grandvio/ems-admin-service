package com.ems.admin.oss;

import com.ems.admin.common.exception.BizException;

import java.util.UUID;

public final class ObjectKeys {
    private ObjectKeys() {
    }

    public static String avatar(long tenantId, long userId, String contentType) {
        return "avatar/" + tenantId + "/" + userId + "/" + uuid() + extension(contentType);
    }

    public static String station(long tenantId, long stationId, String contentType) {
        return "station/" + tenantId + "/" + stationId + "/" + uuid() + extension(contentType);
    }

    public static boolean sameContentType(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return normalize(expected).equals(normalize(actual));
    }

    public static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw BizException.badRequest("只支持 jpeg、png、webp");
        };
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalize(String value) {
        String text = value.trim().toLowerCase();
        int semi = text.indexOf(';');
        if (semi >= 0) {
            text = text.substring(0, semi).trim();
        }
        return text;
    }
}
