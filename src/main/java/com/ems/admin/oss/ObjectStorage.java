package com.ems.admin.oss;

import java.time.Duration;

public interface ObjectStorage {
    String presignPut(String objectKey, String contentType, Duration ttl);

    String presignGet(String objectKey, Duration ttl);

    /**
     * @return 对象不存在时返回 null
     */
    StoredObject head(String objectKey);

    void delete(String objectKey);
}
