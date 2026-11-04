package com.ems.admin.tariff;

import com.ems.admin.energy.entity.EmsEnergySource;
import com.ems.admin.energy.entity.EmsMetricEnergyHour;
import com.ems.admin.energy.mapper.EmsEnergySourceMapper;
import com.ems.admin.energy.mapper.EmsMetricEnergyHourMapper;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.tariff.entity.EmsMarketPrice;
import com.ems.admin.tariff.mapper.EmsMarketPriceMapper;
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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TariffFlowTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmsEnergySourceMapper sourceMapper;
    @Autowired
    private EmsMetricEnergyHourMapper hourMapper;
    @Autowired
    private EmsMarketPriceMapper priceMapper;

    @Test
    void fixedAndMarketTariffsBillTheGateway() throws Exception {
        String token = tenantAdmin();
        Number stationId = createStation(token);
        mockMvc.perform(get("/api/v1/price-locations").header("Authorization", token).param("market", "EU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'EU_DE_LU')].name").value("德国-卢森堡"));
        Number euId = locationId(token, "EU", "EU_DE_LU");
        Number usId = locationId(token, "US", "US_CAISO_NP15");

        mockMvc.perform(put("/api/v1/stations/" + stationId + "/tariff")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"EUR","buyMode":"MARKET","sellMode":"FIXED","sellFixedPrice":0.08}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/stations/" + stationId + "/tariff")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"EUR","buyMode":"MARKET","buyLocationId":%s,"sellMode":"FIXED","sellFixedPrice":0.08}
                                """.formatted(usId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("结算货币需与电价地点一致"));

        mockMvc.perform(put("/api/v1/stations/" + stationId + "/tariff")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"EUR","buyMode":"FIXED","buyFixedPrice":0.25,"sellMode":"FIXED","sellFixedPrice":0.08}
                                """))
                .andExpect(status().isOk());
        seedGate(token, stationId);
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy-cost")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchaseCost[1]").value(2.5))
                .andExpect(jsonPath("$.data.saleIncome[1]").value(0.32))
                .andExpect(jsonPath("$.data.netCost[1]").value(2.18))
                .andExpect(jsonPath("$.data.missingHours[1]").value(0));

        mockMvc.perform(put("/api/v1/stations/" + stationId + "/tariff")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"EUR","buyMode":"MARKET","buyLocationId":%s,"buyAdder":0.12,"sellMode":"FIXED","sellFixedPrice":0.08}
                                """.formatted(euId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/tariff").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buyProduct").value("DAY_AHEAD"))
                .andExpect(jsonPath("$.data.sellProduct").value("DAY_AHEAD"));
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy-cost")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-04"))
                .andExpect(jsonPath("$.data.purchaseCost[1]").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.missingHours[1]").value(1));

        EmsMarketPrice price = new EmsMarketPrice();
        price.setTenantId(0L);
        price.setLocationId(euId.longValue());
        price.setProduct(EmsMarketPrice.DAY_AHEAD);
        price.setIntervalStart(LocalDateTime.of(2026, 11, 4, 0, 0));
        price.setIntervalMinutes(60);
        price.setPricePerMwh(new BigDecimal("80"));
        price.setCurrency("EUR");
        priceMapper.insert(price);

        mockMvc.perform(get("/api/v1/stations/" + stationId + "/prices")
                        .header("Authorization", token)
                        .param("date", "2026-11-04"))
                .andExpect(jsonPath("$.data.marketBuyPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.dayAheadPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.realTimePrice[1]").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.buyPrice[1]").value(0.2))
                .andExpect(jsonPath("$.data.sellPrice[1]").value(0.08));
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy-cost")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-04"))
                .andExpect(jsonPath("$.data.purchaseCost[1]").value(2.0))
                .andExpect(jsonPath("$.data.missingHours[1]").value(0));
    }

    @Test
    void realTimeSettlementKeepsDayAheadForPlanning() throws Exception {
        String token = tenantAdmin();
        Number stationId = createStation(token, "RT", "实时电价站");
        Number euId = locationId(token, "EU", "EU_DE_LU");
        seedGate(token, stationId);
        String bothRealtime = """
                {"currency":"EUR","buyMode":"MARKET","buyLocationId":%s,"buyProduct":"REAL_TIME","buyAdder":0,"sellMode":"MARKET","sellLocationId":%s,"sellProduct":"REAL_TIME","sellAdder":0}
                """.formatted(euId, euId);
        mockMvc.perform(put("/api/v1/stations/" + stationId + "/tariff")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bothRealtime))
                .andExpect(status().isOk());

        insertPrice(euId, EmsMarketPrice.DAY_AHEAD, EmsMarketPrice.SINGLE, "80");
        insertPrice(euId, EmsMarketPrice.REAL_TIME, EmsMarketPrice.SINGLE, "100");
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/prices")
                        .header("Authorization", token)
                        .param("date", "2026-11-04"))
                .andExpect(jsonPath("$.data.marketBuyPrice[1]").value(0.1))
                .andExpect(jsonPath("$.data.marketSellPrice[1]").value(0.1))
                .andExpect(jsonPath("$.data.dayAheadPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.realTimePrice[1]").value(0.1));

        insertPrice(euId, EmsMarketPrice.REAL_TIME, EmsMarketPrice.DOWN, "200");
        insertPrice(euId, EmsMarketPrice.REAL_TIME, EmsMarketPrice.UP, "40");
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/prices")
                        .header("Authorization", token)
                        .param("date", "2026-11-04"))
                .andExpect(jsonPath("$.data.marketBuyPrice[1]").value(0.2))
                .andExpect(jsonPath("$.data.marketSellPrice[1]").value(0.04))
                .andExpect(jsonPath("$.data.dayAheadPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.realTimePrice[1]").value(0.2));
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/prices")
                        .header("Authorization", token)
                        .param("date", "2026-11-04")
                        .param("product", "DAY_AHEAD"))
                .andExpect(jsonPath("$.data.marketBuyPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.marketSellPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.dayAheadPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.realTimePrice[1]").value(0.2));
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy-cost")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-04"))
                .andExpect(jsonPath("$.data.purchaseCost[1]").value(2.0))
                .andExpect(jsonPath("$.data.saleIncome[1]").value(0.16))
                .andExpect(jsonPath("$.data.netCost[1]").value(1.84))
                .andExpect(jsonPath("$.data.missingHours[1]").value(0));

        mockMvc.perform(put("/api/v1/stations/" + stationId + "/tariff")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"EUR","buyMode":"MARKET","buyLocationId":%s,"sellMode":"MARKET","sellLocationId":%s,"sellProduct":"REAL_TIME","sellAdder":0}
                                """.formatted(euId, euId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/tariff").header("Authorization", token))
                .andExpect(jsonPath("$.data.buyProduct").value("DAY_AHEAD"))
                .andExpect(jsonPath("$.data.sellProduct").value("REAL_TIME"));
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/prices")
                        .header("Authorization", token)
                        .param("date", "2026-11-04"))
                .andExpect(jsonPath("$.data.buyPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.sellPrice[1]").value(0.04))
                .andExpect(jsonPath("$.data.dayAheadPrice[1]").value(0.08))
                .andExpect(jsonPath("$.data.realTimePrice[1]").value(0.2));
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/energy-cost")
                        .header("Authorization", token)
                        .param("period", "DAY")
                        .param("date", "2026-11-04"))
                .andExpect(jsonPath("$.data.purchaseCost[1]").value(0.8))
                .andExpect(jsonPath("$.data.saleIncome[1]").value(0.16));
    }

    private void seedGate(String token, Number stationId) throws Exception {
        MvcResult info = mockMvc.perform(get("/api/v1/auth/info").header("Authorization", token)).andReturn();
        Number tenantId = com.jayway.jsonpath.JsonPath.read(info.getResponse().getContentAsString(), "$.data.tenantId");
        TenantContext.set(tenantId.longValue());
        try {
            insertSource(tenantId.longValue(), stationId.longValue(), "importEnergy", "Meter/Epi");
            insertSource(tenantId.longValue(), stationId.longValue(), "exportEnergy", "Meter/Epe");
            insertHour(tenantId.longValue(), stationId.longValue(), "Meter/Epi", "10");
            insertHour(tenantId.longValue(), stationId.longValue(), "Meter/Epe", "4");
        } finally {
            TenantContext.clear();
        }
    }

    private void insertSource(long tenantId, long stationId, String series, String metric) {
        EmsEnergySource source = new EmsEnergySource();
        source.setTenantId(tenantId);
        source.setStationId(stationId);
        source.setSeriesCode(series);
        source.setDeviceId(9001L);
        source.setMetricName(metric);
        sourceMapper.insert(source);
    }

    private void insertHour(long tenantId, long stationId, String metric, String kwh) {
        EmsMetricEnergyHour hour = new EmsMetricEnergyHour();
        hour.setTenantId(tenantId);
        hour.setStationId(stationId);
        hour.setDeviceId(9001L);
        hour.setMetricName(metric);
        hour.setHourStart(LocalDateTime.of(2026, 11, 4, 1, 0));
        hour.setKwh(new BigDecimal(kwh));
        hourMapper.insert(hour);
    }

    private Number locationId(String token, String market, String code) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/price-locations").header("Authorization", token).param("market", market))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> rows = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data");
        return rows.stream().filter(row -> code.equals(row.get("code"))).map(row -> (Number) row.get("id")).findFirst().orElseThrow();
    }

    private void insertPrice(Number locationId, String product, String direction, String pricePerMwh) {
        EmsMarketPrice price = new EmsMarketPrice();
        price.setTenantId(0L);
        price.setLocationId(locationId.longValue());
        price.setProduct(product);
        price.setDirection(direction);
        price.setIntervalStart(LocalDateTime.of(2026, 11, 4, 0, 0));
        price.setIntervalMinutes(60);
        price.setPricePerMwh(new BigDecimal(pricePerMwh));
        price.setCurrency("EUR");
        priceMapper.insert(price);
    }

    private Number createStation(String token) throws Exception {
        return createStation(token, "TARIFF", "电价站");
    }

    private Number createStation(String token, String code, String name) throws Exception {
        MvcResult station = mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","stationType":"STORAGE","status":"RUNNING","timezone":"Europe/Berlin"}
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(station.getResponse().getContentAsString(), "$.data");
    }

    private String tenantAdmin() throws Exception {
        String admin = token("admin", "admin123");
        String suffix = Long.toString(System.nanoTime(), 36);
        mockMvc.perform(post("/api/v1/system/tenants")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"电价租户","code":"%s","adminUsername":"%s","adminPassword":"pass1234"}
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
