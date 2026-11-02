package com.ems.admin.oss;

import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.oss.entity.OssUploadTicket;
import com.ems.admin.oss.mapper.OssUploadTicketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:emsoss;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
class OssFlowTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FakeObjectStorage storage;
    @Autowired
    private OssUploadTicketMapper ticketMapper;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void anonymousCannotRequestTicket() throws Exception {
        mockMvc.perform(post("/api/v1/files/upload-tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"AVATAR","contentType":"image/jpeg","fileSize":1024}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avatarUploadConfirmAndReplace() throws Exception {
        String token = tenantAdmin("ava");
        MvcResult info = mockMvc.perform(get("/api/v1/auth/info").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn();
        Number userId = com.jayway.jsonpath.JsonPath.read(info.getResponse().getContentAsString(), "$.data.userId");
        Number tenantId = com.jayway.jsonpath.JsonPath.read(info.getResponse().getContentAsString(), "$.data.tenantId");

        MvcResult ticket = issue(token, """
                {"bizType":"AVATAR","contentType":"image/jpeg","fileSize":1024}
                """);
        String key = com.jayway.jsonpath.JsonPath.read(ticket.getResponse().getContentAsString(), "$.data.objectKey");
        Number ticketId = com.jayway.jsonpath.JsonPath.read(ticket.getResponse().getContentAsString(), "$.data.ticketId");
        assertTrue(key.startsWith("avatar/" + tenantId.longValue() + "/" + userId.longValue() + "/"));
        assertTrue(key.endsWith(".jpg"));
        mockMvc.perform(post("/api/v1/files/upload-tickets/" + ticketId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/info").header("Authorization", token))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://read.example/" + key));

        storage.headResult = null;
        mockMvc.perform(post("/api/v1/files/upload-tickets/" + ticketId + "/confirm").header("Authorization", token))
                .andExpect(status().isConflict());

        MvcResult second = issue(token, """
                {"bizType":"AVATAR","contentType":"image/png","fileSize":2048}
                """);
        String secondKey = com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.data.objectKey");
        Number secondId = com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.data.ticketId");
        storage.headResult = new StoredObject(2048, "image/png");
        mockMvc.perform(post("/api/v1/files/upload-tickets/" + secondId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        assertTrue(storage.deleted.contains(key));
        mockMvc.perform(get("/api/v1/auth/info").header("Authorization", token))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://read.example/" + secondKey));

        mockMvc.perform(delete("/api/v1/users/me/avatar").header("Authorization", token))
                .andExpect(status().isOk());
        assertTrue(storage.deleted.contains(secondKey));
        mockMvc.perform(get("/api/v1/auth/info").header("Authorization", token))
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rejectsOversizedOrMissingUpload() throws Exception {
        String token = tenantAdmin("big");
        int before = storage.putCount;
        mockMvc.perform(post("/api/v1/files/upload-tickets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"AVATAR","contentType":"image/jpeg","fileSize":3000000}
                                """))
                .andExpect(status().isBadRequest());
        assertEquals(before, storage.putCount);

        MvcResult ticket = issue(token, """
                {"bizType":"AVATAR","contentType":"image/jpeg","fileSize":1024}
                """);
        Number ticketId = com.jayway.jsonpath.JsonPath.read(ticket.getResponse().getContentAsString(), "$.data.ticketId");
        String key = com.jayway.jsonpath.JsonPath.read(ticket.getResponse().getContentAsString(), "$.data.objectKey");
        storage.headResult = null;
        mockMvc.perform(post("/api/v1/files/upload-tickets/" + ticketId + "/confirm").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("文件尚未上传"));
        assertTrue(storage.deleted.isEmpty());

        storage.headResult = new StoredObject(3_000_000, "image/jpeg");
        mockMvc.perform(post("/api/v1/files/upload-tickets/" + ticketId + "/confirm").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("文件类型或大小不符合要求"));
        assertTrue(storage.deleted.contains(key));
        mockMvc.perform(post("/api/v1/files/upload-tickets/" + ticketId + "/confirm").header("Authorization", token))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredTicketIsRejected() throws Exception {
        String token = tenantAdmin("exp");
        MvcResult info = mockMvc.perform(get("/api/v1/auth/info").header("Authorization", token)).andReturn();
        Number tenantId = com.jayway.jsonpath.JsonPath.read(info.getResponse().getContentAsString(), "$.data.tenantId");
        MvcResult ticket = issue(token, """
                {"bizType":"AVATAR","contentType":"image/jpeg","fileSize":1024}
                """);
        Number ticketId = com.jayway.jsonpath.JsonPath.read(ticket.getResponse().getContentAsString(), "$.data.ticketId");
        TenantContext.set(tenantId.longValue());
        try {
            OssUploadTicket saved = ticketMapper.selectById(ticketId.longValue());
            saved.setExpireAt(LocalDateTime.now().minusMinutes(1));
            ticketMapper.updateById(saved);
        } finally {
            TenantContext.clear();
        }
        mockMvc.perform(post("/api/v1/files/upload-tickets/" + ticketId + "/confirm").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("上传凭证已过期"));
    }

    @Test
    void stationImagesCoverAndDelete() throws Exception {
        String token = tenantAdmin("img");
        MvcResult station = mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"OSS1","name":"图片电站","stationType":"STORAGE","status":"RUNNING"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Number stationId = com.jayway.jsonpath.JsonPath.read(station.getResponse().getContentAsString(), "$.data");

        mockMvc.perform(post("/api/v1/files/upload-tickets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"STATION","contentType":"image/jpeg","fileSize":1024}
                                """))
                .andExpect(status().isBadRequest());

        String firstKey = confirmStationImage(token, stationId.longValue(), "image/jpeg", 1024);
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/images").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].cover").value(1))
                .andExpect(jsonPath("$.data[0].url").value("https://read.example/" + firstKey));
        mockMvc.perform(get("/api/v1/stations/" + stationId).header("Authorization", token))
                .andExpect(jsonPath("$.data.coverUrl").value("https://read.example/" + firstKey));

        String secondKey = confirmStationImage(token, stationId.longValue(), "image/png", 2048);
        MvcResult images = mockMvc.perform(get("/api/v1/stations/" + stationId + "/images").header("Authorization", token))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn();
        List<Integer> secondIds = com.jayway.jsonpath.JsonPath.read(images.getResponse().getContentAsString(),
                "$.data[?(@.url=='https://read.example/" + secondKey + "')].id");
        Number secondImageId = secondIds.get(0);
        mockMvc.perform(put("/api/v1/stations/" + stationId + "/images/" + secondImageId + "/cover")
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stations/" + stationId).header("Authorization", token))
                .andExpect(jsonPath("$.data.coverUrl").value("https://read.example/" + secondKey));

        mockMvc.perform(delete("/api/v1/stations/" + stationId + "/images/" + secondImageId)
                        .header("Authorization", token))
                .andExpect(status().isOk());
        assertTrue(storage.deleted.contains(secondKey));
        mockMvc.perform(get("/api/v1/stations/" + stationId).header("Authorization", token))
                .andExpect(jsonPath("$.data.coverUrl").value("https://read.example/" + firstKey));

        mockMvc.perform(delete("/api/v1/stations/" + stationId).header("Authorization", token))
                .andExpect(status().isOk());
        assertTrue(storage.deleted.contains(firstKey));
    }

    @Test
    void viewerCanUploadAvatarButNotStationImage() throws Exception {
        String admin = tenantAdmin("view");
        MvcResult station = mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"OSSV","name":"只读电站","stationType":"STORAGE","status":"RUNNING"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Number stationId = com.jayway.jsonpath.JsonPath.read(station.getResponse().getContentAsString(), "$.data");
        MvcResult roles = mockMvc.perform(get("/api/v1/system/roles?pageSize=100").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();
        List<Integer> viewerIds = com.jayway.jsonpath.JsonPath.read(roles.getResponse().getContentAsString(),
                "$.data.rows[?(@.code=='VIEWER')].id");
        String username = "v" + Long.toString(System.nanoTime(), 36);
        mockMvc.perform(post("/api/v1/system/users")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"pass1234","nickname":"只读","roleIds":[%s]}
                                """.formatted(username, viewerIds.get(0))))
                .andExpect(status().isOk());
        String viewer = token(username, "pass1234");
        mockMvc.perform(post("/api/v1/files/upload-tickets")
                        .header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"STATION","bizId":%s,"contentType":"image/jpeg","fileSize":1024}
                                """.formatted(stationId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/files/upload-tickets")
                        .header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"AVATAR","contentType":"image/jpeg","fileSize":1024}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKey", startsWith("avatar/")))
                .andExpect(jsonPath("$.data.method").value("PUT"));
    }

    private String confirmStationImage(String token, long stationId, String contentType, long size) throws Exception {
        storage.headResult = new StoredObject(size, contentType);
        MvcResult ticket = issue(token, """
                {"bizType":"STATION","bizId":%s,"contentType":"%s","fileSize":%s}
                """.formatted(stationId, contentType, size));
        String key = com.jayway.jsonpath.JsonPath.read(ticket.getResponse().getContentAsString(), "$.data.objectKey");
        Number ticketId = com.jayway.jsonpath.JsonPath.read(ticket.getResponse().getContentAsString(), "$.data.ticketId");
        assertTrue(key.startsWith("station/"));
        assertTrue(key.contains("/" + stationId + "/"));
        mockMvc.perform(post("/api/v1/files/upload-tickets/" + ticketId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        return key;
    }

    private MvcResult issue(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/files/upload-tickets")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl", startsWith("https://upload.example/")))
                .andExpect(jsonPath("$.data.method").value("PUT"))
                .andReturn();
    }

    private String tenantAdmin(String mark) throws Exception {
        String admin = token("admin", "admin123");
        String suffix = mark + Long.toString(System.nanoTime(), 36);
        mockMvc.perform(post("/api/v1/system/tenants")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"OSS租户","code":"%s","adminUsername":"%s","adminPassword":"pass1234"}
                                """.formatted("c" + suffix, "u" + suffix)))
                .andExpect(status().isOk());
        return token("u" + suffix, "pass1234");
    }

    private String token(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","client":"web"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        return "Bearer " + token;
    }

    @TestConfiguration
    static class FakeConfig {
        @Bean
        @Primary
        FakeObjectStorage fakeObjectStorage() {
            return new FakeObjectStorage();
        }
    }
}
