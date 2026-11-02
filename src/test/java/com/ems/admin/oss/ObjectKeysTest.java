package com.ems.admin.oss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectKeysTest {
    @Test
    void buildsKeyAndComparesContentType() {
        String avatar = ObjectKeys.avatar(1, 8, "image/png");
        assertTrue(avatar.startsWith("avatar/1/8/"));
        assertTrue(avatar.endsWith(".png"));
        assertEquals(".jpg", ObjectKeys.extension("image/jpeg"));
        assertTrue(ObjectKeys.sameContentType("image/jpeg", "image/jpeg; charset=UTF-8"));
        assertTrue(ObjectKeys.station(2, 9, "image/webp").startsWith("station/2/9/"));
    }
}
