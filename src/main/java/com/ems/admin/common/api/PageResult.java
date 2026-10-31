package com.ems.admin.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> rows;

    public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> rows) {
        return new PageResult<>(total, pageNum, pageSize, rows);
    }
}
