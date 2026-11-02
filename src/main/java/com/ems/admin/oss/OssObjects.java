package com.ems.admin.oss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public final class OssObjects {
    private static final Logger log = LoggerFactory.getLogger(OssObjects.class);

    private OssObjects() {
    }

    public static void deleteQuietly(ObjectStorage storage, String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ex) {
            log.warn("删除对象失败 key={}", objectKey, ex);
        }
    }
}
