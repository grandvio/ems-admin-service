package com.ems.admin.tsquery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubTableNamesTest {

    @Test
    void sanitizesMetricNameTheSameWayAsSparkplug() {
        String table = SubTableNames.qualified("cloud_db", "group-1", "edge.1", "pcs/01", "soc.a");
        assertEquals("cloud_db.sub_group_1_edge_1_pcs_01_soc_a", table);
    }
}
