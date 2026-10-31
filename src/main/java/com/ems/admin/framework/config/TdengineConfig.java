package com.ems.admin.framework.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class TdengineConfig {

    /**
     * 自定义 JdbcTemplate 会让 Boot 不再自动创建主库模板，这里显式把主库标成 Primary。
     */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "tdJdbcTemplate")
    public JdbcTemplate tdJdbcTemplate(EmsProperties properties) {
        EmsProperties.Tdengine td = properties.getTdengine();
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("tdengine");
        ds.setDriverClassName("com.taosdata.jdbc.rs.RestfulDriver");
        ds.setJdbcUrl(buildUrl(td));
        ds.setUsername(td.getUsername());
        ds.setPassword(td.getPassword() == null ? "" : td.getPassword());
        ds.setMaximumPoolSize(4);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(Math.max(td.getConnectionTimeoutMs(), 200));
        ds.setInitializationFailTimeout(-1);
        return new JdbcTemplate(ds);
    }

    private String buildUrl(EmsProperties.Tdengine td) {
        String url = td.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("未配置 ems.tdengine.url");
        }
        if (url.contains("user=")) {
            return url;
        }
        String joiner = url.contains("?") ? "&" : "?";
        return url + joiner + "user=" + td.getUsername() + "&password=" + (td.getPassword() == null ? "" : td.getPassword());
    }
}
