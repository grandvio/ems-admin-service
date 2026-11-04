package com.ems.admin.tariff;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class EntsoePriceParser {
    public record Point(Instant start, int minutes, BigDecimal pricePerMwh, String direction) {
        public Point(Instant start, int minutes, BigDecimal pricePerMwh) {
            this(start, minutes, pricePerMwh, "SINGLE");
        }
    }

    private EntsoePriceParser() {
    }

    public static List<Point> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            Document document = document(xml);
            List<Point> points = new ArrayList<>();
            for (Element period : elements(document, "Period")) {
                Element interval = firstChild(period, "timeInterval");
                Element startElement = interval == null ? null : firstChild(interval, "start");
                Element resolution = firstChild(period, "resolution");
                if (startElement == null || resolution == null) {
                    continue;
                }
                Instant start = instant(text(startElement));
                int minutes = minutes(text(resolution));
                for (Element point : childElements(period, "Point")) {
                    Element position = firstChild(point, "position");
                    Element amount = firstChild(point, "price.amount");
                    if (position == null || amount == null) {
                        continue;
                    }
                    int index = Integer.parseInt(text(position));
                    Instant pointStart = start.plus(Duration.ofMinutes((long) (index - 1) * minutes));
                    points.add(new Point(pointStart, minutes, new BigDecimal(text(amount))));
                }
            }
            return points;
        } catch (Exception ex) {
            throw new IllegalArgumentException("ENTSO-E 电价文档无法解析", ex);
        }
    }

    public static List<Point> parseImbalancePayload(byte[] body) {
        if (body == null || body.length == 0) {
            return List.of();
        }
        try {
            if (body.length >= 4 && body[0] == 'P' && body[1] == 'K') {
                List<Point> points = new ArrayList<>();
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.isDirectory() || entry.getName() == null || !entry.getName().toLowerCase().endsWith(".xml")) {
                            continue;
                        }
                        String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                        if (xml.contains("Acknowledgement_MarketDocument")) {
                            continue;
                        }
                        points.addAll(parseImbalance(xml));
                    }
                }
                return points;
            }
            String xml = new String(body, StandardCharsets.UTF_8);
            if (xml.contains("Acknowledgement_MarketDocument")) {
                return List.of();
            }
            return parseImbalance(xml);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("ENTSO-E 不平衡电价文档无法解析", ex);
        }
    }

    public static List<Point> parseImbalance(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            Document document = document(xml);
            List<Point> points = new ArrayList<>();
            for (Element period : elements(document, "Period")) {
                Element interval = firstChild(period, "timeInterval");
                Element startElement = interval == null ? null : firstChild(interval, "start");
                Element resolution = firstChild(period, "resolution");
                if (startElement == null || resolution == null) {
                    continue;
                }
                Instant start = instant(text(startElement));
                int minutes = minutes(text(resolution));
                String seriesDirection = flowDirection(ancestor(period, "TimeSeries"));
                for (Element point : childElements(period, "Point")) {
                    Element position = firstChild(point, "position");
                    Element amount = firstChild(point, "imbalance_Price.amount");
                    if (amount == null) {
                        amount = firstChild(point, "price.amount");
                    }
                    if (position == null || amount == null) {
                        continue;
                    }
                    Element category = firstChild(point, "imbalance_Price.category");
                    String direction = direction(category == null ? null : text(category));
                    if (direction == null) {
                        direction = seriesDirection == null ? "SINGLE" : seriesDirection;
                    }
                    int index = Integer.parseInt(text(position));
                    Instant pointStart = start.plus(Duration.ofMinutes((long) (index - 1) * minutes));
                    points.add(new Point(pointStart, minutes, new BigDecimal(text(amount)), direction));
                }
            }
            return points;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("ENTSO-E 不平衡电价文档无法解析", ex);
        }
    }

    private static Document document(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String flowDirection(Element series) {
        if (series == null) {
            return null;
        }
        Element direct = firstChild(series, "flowDirection.direction");
        if (direct != null) {
            return direction(text(direct));
        }
        Element flow = firstChild(series, "flowDirection");
        Element nested = flow == null ? null : firstChild(flow, "direction");
        return nested == null ? null : direction(text(nested));
    }

    private static String direction(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return switch (code.trim().toUpperCase()) {
            case "A04", "A01" -> "UP";
            case "A05", "A02" -> "DOWN";
            default -> "SINGLE";
        };
    }

    private static Element ancestor(Element element, String localName) {
        Node current = element.getParentNode();
        while (current instanceof Element parent) {
            if (localName.equalsIgnoreCase(parent.getLocalName())) {
                return parent;
            }
            current = parent.getParentNode();
        }
        return null;
    }

    private static Instant instant(String text) {
        String value = text;
        if (value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}Z")) {
            value = value.substring(0, 16) + ":00Z";
        }
        return Instant.parse(value);
    }

    private static int minutes(String resolution) {
        return switch (resolution) {
            case "PT15M" -> 15;
            case "PT30M" -> 30;
            case "PT60M", "PT1H" -> 60;
            default -> throw new IllegalArgumentException("不支持的电价间隔 " + resolution);
        };
    }

    private static List<Element> elements(Document document, String localName) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                found.add(element);
            }
        }
        return found;
    }

    private static List<Element> childElements(Element parent, String localName) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                found.add(element);
            }
        }
        return found;
    }

    private static Element firstChild(Element parent, String localName) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && element.getLocalName() != null
                    && localName.equalsIgnoreCase(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private static String text(Element element) {
        return element.getTextContent().trim();
    }
}
