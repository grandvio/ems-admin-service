package com.ems.admin.energy;

import com.ems.admin.energy.entity.EmsMetricEnergyHour;
import com.ems.admin.energy.mapper.EmsMetricEnergyHourMapper;
import com.ems.admin.framework.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnergyFlowTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmsMetricEnergyHourMapper hourMapper;

    @Test
    void sourceRequiresCumulativeEnergyPoint() throws Exception {
        String token = tenantAdmin("src");
        Number stationId = createStation(token, "ESRC");
        Number deviceId = createDevice(token, "M1", stationId);
        mockMvc.perform(put("/api/v1/stations/" + stationId + "/energy-sources/importEnergy")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sources":[{"deviceId":%s,"metricName":"Meter/Epi"}]}
                                """.formatted(deviceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("请先绑定物模型"));

        Number productId = publishProduct(token, "meter");
        mockMvc.perform(put("/api/v1/devices/" + deviceId + "/product")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/stations/" + stationId + "/energy-sources/importEnergy")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sources":[{"deviceId":%s,"metricName":"Pcs/P"}]}
                                """.formatted(deviceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("点位单位必须是 kWh"));
        mockMvc.perform(put("/api/v1/stations/" + stationId + "/energy-sources/importEnergy")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sources":[{"deviceId":%s,"metricName":"Meter/Epi"}]}
                                """.formatted(deviceId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/stations/" + stationId + "/energy-sources/exportEnergy")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sources":[{"deviceId":%s,"metricName":"Meter/Epi"}]}
                                """.formatted(deviceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("该点位已用于买电量"));
    }

    @Test
    void dayChartUsesConfiguredPoints() throws Exception {
        String token = tenantAdmin("day");
        Number stationId = createStation(token, "EDAY");
        Number gridId = createDevice(token, "M1", stationId);
        Number pvId = createDevice(token, "M2", stationId);
        Number productId = publishProduct(token, "meter");
        bind(token, gridId, productId);
        bind(token, pvId, productId);
        putSource(token, stationId, "importEnergy", gridId, "Meter/Epi");
        putSource(token, stationId, "generation", pvId, "Meter/Epi");

        MvcResult info = mockMvc.perform(get("/api/v1/auth/info").header("Authorization", token)).andReturn();
        Number tenantId = com.jayway.jsonpath.JsonPath.read(info.getResponse().getContentAsString(), "$.data.tenantId");
        TenantContext.set(tenantId.longValue());
        try {
            insertHour(tenantId.longValue(), stationId.longValue(), gridId.longValue(), "Meter/Epi", "1.500");
            insertHour(tenantId.longValue(), stationId.longValue(), pvId.longValue(), "Meter/Epi", "2.000");
            insertHour(tenantId.longValue(), stationId.longValue(), gridId.longValue(), "Load/Epi", "9.000");
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buckets.length()").value(24))
                .andExpect(jsonPath("$.data.series.importEnergy[1]").value(1.5))
                .andExpect(jsonPath("$.data.series.generation[1]").value(2.0))
                .andExpect(jsonPath("$.data.series.consumption[1]").value(3.5));

        MvcResult sources = mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy-sources")
                        .header("Authorization", token))
                .andExpect(jsonPath("$.data.importEnergy[0].metricName").value("Meter/Epi"))
                .andReturn();
        Number sourceId = com.jayway.jsonpath.JsonPath.read(sources.getResponse().getContentAsString(), "$.data.importEnergy[0].id");
        mockMvc.perform(delete("/api/v1/stations/" + stationId + "/energy-sources/" + sourceId)
                        .header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-02"))
                .andExpect(jsonPath("$.data.series.importEnergy[1]").value(0))
                .andExpect(jsonPath("$.data.series.consumption[1]").value(2.0));

        putSource(token, stationId, "importEnergy", gridId, "Meter/Epi");
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-02"))
                .andExpect(jsonPath("$.data.series.importEnergy[1]").value(1.5));

        putSource(token, stationId, "consumption", gridId, "Load/Epi");
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-02"))
                .andExpect(jsonPath("$.data.series.consumption[1]").value(9.0));

        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy")
                        .header("Authorization", token)
                        .param("period", "DAY"))
                .andExpect(status().isBadRequest());
    }

    private void putSource(String token, Number stationId, String series, Number deviceId, String metric) throws Exception {
        mockMvc.perform(put("/api/v1/stations/" + stationId + "/energy-sources/" + series)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sources":[{"deviceId":%s,"metricName":"%s"}]}
                                """.formatted(deviceId, metric)))
                .andExpect(status().isOk());
    }

    private void bind(String token, Number deviceId, Number productId) throws Exception {
        mockMvc.perform(put("/api/v1/devices/" + deviceId + "/product")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + "}"))
                .andExpect(status().isOk());
    }

    private void insertHour(long tenantId, long stationId, long deviceId, String metric, String kwh) {
        EmsMetricEnergyHour hour = new EmsMetricEnergyHour();
        hour.setTenantId(tenantId);
        hour.setStationId(stationId);
        hour.setDeviceId(deviceId);
        hour.setMetricName(metric);
        hour.setHourStart(LocalDateTime.of(2026, 11, 2, 1, 0));
        hour.setKwh(new BigDecimal(kwh));
        hourMapper.insert(hour);
    }

    private Number createDevice(String token, String code, Number stationId) throws Exception {
        MvcResult gateway = mockMvc.perform(post("/api/v1/gateways")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationId":%s,"name":"网关","groupId":"g-%s","edgeNodeId":"e-%s"}
                                """.formatted(stationId, code, code)))
                .andExpect(status().isOk())
                .andReturn();
        Number gatewayId = com.jayway.jsonpath.JsonPath.read(gateway.getResponse().getContentAsString(), "$.data");
        MvcResult device = mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gatewayId":%s,"name":"设备","deviceCode":"dev-%s"}
                                """.formatted(gatewayId, code)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(device.getResponse().getContentAsString(), "$.data");
    }

    private Number createStation(String token, String code) throws Exception {
        MvcResult station = mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"电量站","stationType":"STORAGE","status":"RUNNING","timezone":"Asia/Shanghai"}
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(station.getResponse().getContentAsString(), "$.data");
    }

    private Number publishProduct(String token, String code) throws Exception {
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"产品"}
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andReturn();
        Number productId = com.jayway.jsonpath.JsonPath.read(product.getResponse().getContentAsString(), "$.data");
        addProperty(token, productId, "Meter/Epi", "kWh");
        addProperty(token, productId, "Load/Epi", "kWh");
        addProperty(token, productId, "Pcs/P", "kW");
        mockMvc.perform(post("/api/v1/products/" + productId + "/publish").header("Authorization", token))
                .andExpect(status().isOk());
        return productId;
    }

    private void addProperty(String token, Number productId, String identifier, String unit) throws Exception {
        mockMvc.perform(post("/api/v1/products/" + productId + "/properties")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","name":"%s","dataType":"float","unit":"%s","accessMode":"r","groupCode":"TELEMETRY"}
                                """.formatted(identifier, identifier, unit)))
                .andExpect(status().isOk());
    }

    private String tenantAdmin(String mark) throws Exception {
        String admin = token("admin", "admin123");
        String suffix = mark + Long.toString(System.nanoTime(), 36);
        mockMvc.perform(post("/api/v1/system/tenants")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"电量租户","code":"%s","adminUsername":"%s","adminPassword":"pass1234"}
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
        String value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        return "Bearer " + value;
    }
}
