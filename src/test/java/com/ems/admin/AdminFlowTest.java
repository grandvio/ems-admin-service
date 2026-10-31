package com.ems.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/stations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void tenantStationDeviceAndMetricFlow() throws Exception {
        String admin = token("admin", "admin123");
        mockMvc.perform(get("/api/v1/auth/info").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasItem("system:tenant:add")))
                .andExpect(jsonPath("$.data.tenantId").value(0));

        MvcResult created = mockMvc.perform(post("/api/v1/system/tenants")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"演示能源","code":"demo","adminUsername":"demoadmin","adminPassword":"pass1234","adminNickname":"演示管理员"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        String tenantAdmin = token("demoadmin", "pass1234");
        mockMvc.perform(post("/api/v1/system/tenants")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"越权","code":"hack","adminUsername":"hackadmin","adminPassword":"pass1234"}
                                """))
                .andExpect(status().isForbidden());

        MvcResult station = mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"S001","name":"一号储能站","stationType":"STORAGE","status":"RUNNING","capacityKw":100,"storageKwh":200}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Number stationId = com.jayway.jsonpath.JsonPath.read(station.getResponse().getContentAsString(), "$.data");

        MvcResult gateway = mockMvc.perform(post("/api/v1/gateways")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationId":%s,"name":"边缘网关","groupId":"group-1","edgeNodeId":"edge.1"}
                                """.formatted(stationId)))
                .andExpect(status().isOk())
                .andReturn();
        Number gatewayId = com.jayway.jsonpath.JsonPath.read(gateway.getResponse().getContentAsString(), "$.data");

        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"pcs","name":"储能变流器"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        Number productId = com.jayway.jsonpath.JsonPath.read(product.getResponse().getContentAsString(), "$.data");

        mockMvc.perform(post("/api/v1/products/" + productId + "/properties")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"soc.a","name":"SOC","dataType":"float","unit":"%","accessMode":"r","groupCode":"TELEMETRY"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/" + productId + "/events")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"over_temp","name":"过温","level":"CRITICAL"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/" + productId + "/services")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"start","name":"启动","inputParams":"{}"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/products/" + productId + "/publish")
                        .header("Authorization", tenantAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        MvcResult device = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gatewayId":%s,"name":"1号PCS","deviceCode":"pcs/01"}
                                """.formatted(gatewayId)))
                .andExpect(status().isOk())
                .andReturn();
        Number deviceId = com.jayway.jsonpath.JsonPath.read(device.getResponse().getContentAsString(), "$.data");
        mockMvc.perform(put("/api/v1/devices/" + deviceId + "/product")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/devices/" + deviceId + "/metrics/latest")
                        .header("Authorization", tenantAdmin))
                .andExpect(status().isServiceUnavailable());

        MvcResult roles = mockMvc.perform(get("/api/v1/system/roles")
                        .header("Authorization", tenantAdmin)
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", greaterThan(0)))
                .andReturn();
        java.util.List<Number> viewerRoleIds = com.jayway.jsonpath.JsonPath.read(roles.getResponse().getContentAsString(),
                "$.data.rows[?(@.code=='VIEWER')].id");
        Number viewerRoleId = viewerRoleIds.get(0);

        mockMvc.perform(post("/api/v1/system/users")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"viewer1","password":"pass1234","nickname":"只读","roleIds":[%s]}
                                """.formatted(viewerRoleId)))
                .andExpect(status().isOk());
        String viewer = token("viewer1", "pass1234");
        mockMvc.perform(get("/api/v1/stations").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(put("/api/v1/system/roles/" + viewerRoleId + "/stations")
                        .header("Authorization", tenantAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stationIds\":[" + stationId + "]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stations").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(delete("/api/v1/system/tenants/" + com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data"))
                        .header("Authorization", admin))
                .andExpect(status().isConflict());
    }

    private String token(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","client":"web"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        return "Bearer " + token;
    }
}
