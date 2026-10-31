package com.ems.admin.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ems")
public class EmsProperties {
    private final Jwt jwt = new Jwt();
    private final Tdengine tdengine = new Tdengine();

    @Data
    public static class Jwt {
        private String secret = "ems-admin-dev-jwt-secret-change-me-32b";
        private long expireMinutes = 720;
    }

    @Data
    public static class Tdengine {
        private String url;
        private String username;
        private String password;
        private String database = "cloud_db";
        private long connectionTimeoutMs = 3000;
    }
}
