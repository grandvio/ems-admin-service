package com.ems.admin.oss;

import com.ems.admin.framework.config.EmsProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunObjectStorageTest {
    @Test
    void presignedUrlUsesCustomDomain() {
        EmsProperties properties = new EmsProperties();
        properties.getOss().setEndpoint("https://oss-cn-hongkong.aliyuncs.com");
        properties.getOss().setPublicEndpoint("https://dev-oss-rn.renoncloudx.com");
        properties.getOss().setBucket("dev-renon-power");
        properties.getOss().setAccessKeyId("LTAItesttesttest");
        properties.getOss().setAccessKeySecret("abcdefghijklmnopqrstuvwxyz123456");
        AliyunObjectStorage storage = new AliyunObjectStorage(properties);
        try {
            String upload = storage.presignPut("avatar/1/2/a.jpg", "image/jpeg", Duration.ofMinutes(10));
            String read = storage.presignGet("avatar/1/2/a.jpg", Duration.ofHours(2));
            assertTrue(upload.startsWith("https://dev-oss-rn.renoncloudx.com/avatar/1/2/a.jpg?"), upload);
            assertTrue(read.startsWith("https://dev-oss-rn.renoncloudx.com/avatar/1/2/a.jpg?"), read);
        } finally {
            storage.destroy();
        }
    }
}
