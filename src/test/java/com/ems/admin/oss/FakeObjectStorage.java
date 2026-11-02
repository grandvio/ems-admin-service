package com.ems.admin.oss;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

class FakeObjectStorage implements ObjectStorage {
    final List<String> deleted = new ArrayList<>();
    StoredObject headResult = new StoredObject(1024, "image/jpeg");
    int putCount;

    void reset() {
        deleted.clear();
        headResult = new StoredObject(1024, "image/jpeg");
        putCount = 0;
    }

    @Override
    public String presignPut(String objectKey, String contentType, Duration ttl) {
        putCount++;
        return "https://upload.example/" + objectKey;
    }

    @Override
    public String presignGet(String objectKey, Duration ttl) {
        return "https://read.example/" + objectKey;
    }

    @Override
    public StoredObject head(String objectKey) {
        return headResult;
    }

    @Override
    public void delete(String objectKey) {
        deleted.add(objectKey);
    }
}
