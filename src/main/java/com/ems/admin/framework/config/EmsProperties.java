package com.ems.admin.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ems")
public class EmsProperties {
    private final Jwt jwt = new Jwt();
    private final Tdengine tdengine = new Tdengine();
    private final Oss oss = new Oss();
    private final Price price = new Price();

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

    @Data
    public static class Oss {
        private String endpoint = "https://oss-cn-hongkong.aliyuncs.com";
        private String publicEndpoint = "https://dev-oss-rn.renoncloudx.com";
        private String bucket = "dev-renon-power";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private long uploadExpireMinutes = 10;
        private long readExpireMinutes = 120;
        private long avatarMaxBytes = 2L * 1024 * 1024;
        private long stationImageMaxBytes = 5L * 1024 * 1024;
        private int stationImageMaxCount = 9;
    }

    @Data
    public static class Price {
        private boolean syncEnabled = true;
        private String entsoeSecurityToken = "";
        private String entsoeEndpoint = "https://web-api.tp.entsoe.eu/api";
    }
}
