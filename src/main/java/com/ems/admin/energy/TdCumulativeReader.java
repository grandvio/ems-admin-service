package com.ems.admin.energy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

@Component
public class TdCumulativeReader implements CumulativeReader {
    private final JdbcTemplate tdJdbcTemplate;

    public TdCumulativeReader(@Qualifier("tdJdbcTemplate") JdbcTemplate tdJdbcTemplate) {
        this.tdJdbcTemplate = tdJdbcTemplate;
    }

    @Override
    public List<Sample> readAfter(String table, Long afterEpochMilli, int limit) {
        String sql = "SELECT ts, val_float FROM " + table
                + (afterEpochMilli == null ? "" : " WHERE ts > ?")
                + " ORDER BY ts ASC LIMIT " + limit;
        if (afterEpochMilli == null) {
            return tdJdbcTemplate.query(sql, (rs, rowNum) -> map(rs));
        }
        return tdJdbcTemplate.query(sql, (rs, rowNum) -> map(rs), new Timestamp(afterEpochMilli));
    }

    private Sample map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp("ts");
        double value = rs.getDouble("val_float");
        Double reading = rs.wasNull() ? null : value;
        long epoch = ts == null ? 0L : ts.getTime();
        return new Sample(epoch, reading);
    }
}
