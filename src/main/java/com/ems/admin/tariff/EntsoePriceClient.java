package com.ems.admin.tariff;

import com.ems.admin.framework.config.EmsProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class EntsoePriceClient {
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final EmsProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public EntsoePriceClient(EmsProperties properties) {
        this.properties = properties;
    }

    public List<EntsoePriceParser.Point> fetch(String eic, Instant start, Instant end) {
        String token = properties.getPrice().getEntsoeSecurityToken();
        String query = "securityToken=" + encode(token)
                + "&documentType=A44"
                + "&contract_MarketAgreement.type=A01"
                + "&in_Domain=" + encode(eic)
                + "&out_Domain=" + encode(eic)
                + "&periodStart=" + PERIOD.format(start)
                + "&periodEnd=" + PERIOD.format(end);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getPrice().getEntsoeEndpoint() + "?" + query))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("ENTSO-E 响应 " + response.statusCode());
            }
            if (response.body() != null && response.body().contains("Acknowledgement_MarketDocument")) {
                return List.of();
            }
            return EntsoePriceParser.parse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ENTSO-E 请求被中断", ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ENTSO-E 请求失败", ex);
        }
    }

    public List<EntsoePriceParser.Point> fetchImbalance(String eic, Instant start, Instant end) {
        String token = properties.getPrice().getEntsoeSecurityToken();
        String query = "securityToken=" + encode(token)
                + "&documentType=A85"
                + "&controlArea_Domain=" + encode(eic)
                + "&periodStart=" + PERIOD.format(start)
                + "&periodEnd=" + PERIOD.format(end);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getPrice().getEntsoeEndpoint() + "?" + query))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("ENTSO-E 响应 " + response.statusCode());
            }
            return EntsoePriceParser.parseImbalancePayload(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ENTSO-E 请求被中断", ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ENTSO-E 请求失败", ex);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
