package com.ems.admin.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public final class Pages {
    private Pages() {
    }

    public static <T> Page<T> of(long pageNum, long pageSize) {
        long current = Math.max(pageNum, 1);
        long size = Math.min(Math.max(pageSize, 1), 100);
        return new Page<>(current, size);
    }
}
