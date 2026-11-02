package com.ems.admin.oss;

import com.ems.admin.framework.config.EmsProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class OssUrls {
    private final ObjectStorage objectStorage;
    private final EmsProperties properties;

    public OssUrls(ObjectStorage objectStorage, EmsProperties properties) {
        this.objectStorage = objectStorage;
        this.properties = properties;
    }

    public String read(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        return objectStorage.presignGet(objectKey, Duration.ofMinutes(properties.getOss().getReadExpireMinutes()));
    }
}
