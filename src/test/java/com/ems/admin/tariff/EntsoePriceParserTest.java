package com.ems.admin.tariff;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntsoePriceParserTest {
    @Test
    void readsQuarterHourPricesIncludingNegative() {
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Publication_MarketDocument xmlns="urn:iec62325.351:tc57wg16:451-3:publicationdocument:7:3">
                  <TimeSeries>
                    <Period>
                      <timeInterval>
                        <start>2026-11-03T23:00Z</start>
                        <end>2026-11-04T23:00Z</end>
                      </timeInterval>
                      <resolution>PT15M</resolution>
                      <Point><position>1</position><price.amount>80.00</price.amount></Point>
                      <Point><position>2</position><price.amount>100.00</price.amount></Point>
                      <Point><position>3</position><price.amount>-20.00</price.amount></Point>
                      <Point><position>4</position><price.amount>40.00</price.amount></Point>
                    </Period>
                  </TimeSeries>
                </Publication_MarketDocument>
                """;
        List<EntsoePriceParser.Point> points = EntsoePriceParser.parse(xml);
        assertEquals(4, points.size());
        assertEquals(Instant.parse("2026-11-03T23:00:00Z"), points.get(0).start());
        assertEquals(15, points.get(0).minutes());
        assertEquals(new BigDecimal("-20.00"), points.get(2).pricePerMwh());
        assertEquals(Instant.parse("2026-11-03T23:45:00Z"), points.get(3).start());
        assertEquals("SINGLE", points.get(0).direction());
    }

    @Test
    void readsShortageAndSurplusFromImbalanceDocument() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <Balancing_MarketDocument xmlns="urn:iec62325.351:tc57wg16:451-6:balancingdocument:4:1">
                  <TimeSeries>
                    <Period>
                      <timeInterval>
                        <start>2026-11-03T23:00Z</start>
                        <end>2026-11-04T00:00Z</end>
                      </timeInterval>
                      <resolution>PT15M</resolution>
                      <Point>
                        <position>1</position>
                        <imbalance_Price.amount>120.00</imbalance_Price.amount>
                        <imbalance_Price.category>A05</imbalance_Price.category>
                      </Point>
                      <Point>
                        <position>1</position>
                        <imbalance_Price.amount>30.00</imbalance_Price.amount>
                        <imbalance_Price.category>A04</imbalance_Price.category>
                      </Point>
                    </Period>
                  </TimeSeries>
                  <TimeSeries>
                    <flowDirection.direction>A01</flowDirection.direction>
                    <Period>
                      <timeInterval>
                        <start>2026-11-03T23:00Z</start>
                        <end>2026-11-04T00:00Z</end>
                      </timeInterval>
                      <resolution>PT15M</resolution>
                      <Point>
                        <position>2</position>
                        <imbalance_Price.amount>15.00</imbalance_Price.amount>
                      </Point>
                    </Period>
                  </TimeSeries>
                </Balancing_MarketDocument>
                """;
        List<EntsoePriceParser.Point> points = EntsoePriceParser.parseImbalancePayload(zip(xml));
        assertEquals(3, points.size());
        assertEquals("DOWN", points.get(0).direction());
        assertEquals(new BigDecimal("120.00"), points.get(0).pricePerMwh());
        assertEquals("UP", points.get(1).direction());
        assertEquals(new BigDecimal("30.00"), points.get(1).pricePerMwh());
        assertEquals(Instant.parse("2026-11-03T23:00:00Z"), points.get(0).start());
        assertEquals(Instant.parse("2026-11-03T23:00:00Z"), points.get(1).start());
        assertEquals("UP", points.get(2).direction());
        assertEquals(Instant.parse("2026-11-03T23:15:00Z"), points.get(2).start());
        assertTrue(EntsoePriceParser.parseImbalancePayload(
                "<Acknowledgement_MarketDocument/>".getBytes(StandardCharsets.UTF_8)).isEmpty());
    }

    private byte[] zip(String xml) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("imbalance.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }
}
