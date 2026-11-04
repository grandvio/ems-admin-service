package com.ems.admin.energy;

import java.util.List;

public interface CumulativeReader {
    /**
     * @param afterEpochMilli 为空时从最早的点开始
     */
    List<Sample> readAfter(String table, Long afterEpochMilli, int limit);

    record Sample(long epochMilli, Double value) {
    }
}
