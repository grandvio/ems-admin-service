package com.ems.admin.common;

public final class TenantIds {
    /** 平台租户。标准产品、系统菜单归属这里。 */
    public static final long PLATFORM = 0L;

    private TenantIds() {
    }

    public static boolean isPlatform(Long tenantId) {
        return tenantId != null && tenantId == PLATFORM;
    }
}
