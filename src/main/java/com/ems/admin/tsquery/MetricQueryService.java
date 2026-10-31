package com.ems.admin.tsquery;

import com.ems.admin.asset.entity.EmsDevice;
import com.ems.admin.asset.entity.EmsGateway;
import com.ems.admin.asset.service.DeviceService;
import com.ems.admin.asset.service.GatewayService;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.config.EmsProperties;
import com.ems.admin.thingmodel.entity.IotProperty;
import com.ems.admin.thingmodel.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class MetricQueryService {
    private static final long MAX_RANGE_MS = 31L * 24 * 60 * 60 * 1000;
    private static final int HISTORY_LIMIT = 5000;

    private final DeviceService deviceService;
    private final GatewayService gatewayService;
    private final ProductService productService;
    private final JdbcTemplate tdJdbcTemplate;
    private final EmsProperties properties;

    public MetricQueryService(DeviceService deviceService, GatewayService gatewayService, ProductService productService,
                              @Qualifier("tdJdbcTemplate") JdbcTemplate tdJdbcTemplate, EmsProperties properties) {
        this.deviceService = deviceService;
        this.gatewayService = gatewayService;
        this.productService = productService;
        this.tdJdbcTemplate = tdJdbcTemplate;
        this.properties = properties;
    }

    public List<MetricDtos.LatestItem> latest(Long deviceId, String identifiers) {
        Resolved resolved = resolve(deviceId, identifiers);
        List<MetricDtos.LatestItem> items = new ArrayList<>();
        for (IotProperty property : resolved.properties()) {
            String table = table(resolved.gateway(), resolved.device(), property.getIdentifier());
            MetricDtos.Point point = queryLatest(table, property.getDataType());
            MetricDtos.LatestItem item = new MetricDtos.LatestItem();
            item.setIdentifier(property.getIdentifier());
            item.setName(property.getName());
            item.setUnit(property.getUnit());
            item.setDataType(property.getDataType());
            if (point != null) {
                item.setTs(point.getTs());
                item.setValue(point.getValue());
                item.setQuality(point.getQuality());
            }
            items.add(item);
        }
        return items;
    }

    public MetricDtos.History history(Long deviceId, String identifier, long start, long end) {
        if (!StringUtils.hasText(identifier)) {
            throw BizException.badRequest("属性标识不能为空");
        }
        if (end < start) {
            throw BizException.badRequest("结束时间不能早于开始时间");
        }
        if (end - start > MAX_RANGE_MS) {
            throw BizException.badRequest("查询跨度不能超过 31 天");
        }
        Resolved resolved = resolve(deviceId, identifier);
        IotProperty property = resolved.properties().get(0);
        String table = table(resolved.gateway(), resolved.device(), property.getIdentifier());
        String sql = "SELECT ts, val_float, val_int, val_bool, val_str, quality FROM " + table
                + " WHERE ts >= ? AND ts <= ? ORDER BY ts ASC LIMIT " + (HISTORY_LIMIT + 1);
        List<MetricDtos.Point> points;
        try {
            points = tdJdbcTemplate.query(sql, (rs, rowNum) -> map(rs, property.getDataType()), new Timestamp(start), new Timestamp(end));
        } catch (DataAccessException e) {
            if (isDown(e)) {
                throw BizException.unavailable("时序库不可用");
            }
            log.warn("历史曲线无数据 {} : {}", table, rootMessage(e));
            points = List.of();
        }
        boolean truncated = points.size() > HISTORY_LIMIT;
        if (truncated) {
            points = points.subList(0, HISTORY_LIMIT);
        }
        MetricDtos.History history = new MetricDtos.History();
        history.setIdentifier(property.getIdentifier());
        history.setName(property.getName());
        history.setUnit(property.getUnit());
        history.setDataType(property.getDataType());
        history.setTruncated(truncated);
        history.setPoints(points);
        return history;
    }

    private MetricDtos.Point queryLatest(String table, String dataType) {
        String sql = "SELECT ts, val_float, val_int, val_bool, val_str, quality FROM " + table + " ORDER BY ts DESC LIMIT 1";
        try {
            List<MetricDtos.Point> rows = tdJdbcTemplate.query(sql, (rs, rowNum) -> map(rs, dataType));
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException e) {
            if (isDown(e)) {
                throw BizException.unavailable("时序库不可用");
            }
            log.warn("最新值无数据 {} : {}", table, rootMessage(e));
            return null;
        }
    }

    private MetricDtos.Point map(ResultSet rs, String dataType) throws SQLException {
        MetricDtos.Point point = new MetricDtos.Point();
        Timestamp ts = rs.getTimestamp(1);
        point.setTs(ts == null ? null : ts.getTime());
        point.setValue(readValue(rs, dataType));
        int quality = rs.getInt(6);
        point.setQuality(rs.wasNull() ? null : quality);
        return point;
    }

    private Object readValue(ResultSet rs, String dataType) throws SQLException {
        return switch (dataType) {
            case "float" -> {
                double value = rs.getDouble(2);
                yield rs.wasNull() ? null : value;
            }
            case "int" -> {
                long value = rs.getLong(3);
                yield rs.wasNull() ? null : value;
            }
            case "bool" -> {
                boolean value = rs.getBoolean(4);
                yield rs.wasNull() ? null : value;
            }
            default -> rs.getString(5);
        };
    }

    private String table(EmsGateway gateway, EmsDevice device, String identifier) {
        return SubTableNames.qualified(properties.getTdengine().getDatabase(), gateway.getGroupId(), gateway.getEdgeNodeId(),
                device.getDeviceCode(), identifier);
    }

    private Resolved resolve(Long deviceId, String identifiers) {
        EmsDevice device = deviceService.require(deviceId);
        if (device.getProductId() == null) {
            throw BizException.badRequest("设备未绑定产品");
        }
        EmsGateway gateway = gatewayService.require(device.getGatewayId());
        List<IotProperty> all = productService.propertiesOf(device.getProductId());
        List<IotProperty> selected;
        if (!StringUtils.hasText(identifiers)) {
            selected = all;
        } else {
            List<String> wanted = Arrays.stream(identifiers.split(",")).map(String::trim).filter(StringUtils::hasText).distinct().toList();
            selected = new ArrayList<>();
            for (String identifier : wanted) {
                IotProperty property = all.stream().filter(item -> identifier.equals(item.getIdentifier())).findFirst()
                        .orElseThrow(() -> BizException.badRequest("属性不存在: " + identifier));
                selected.add(property);
            }
        }
        return new Resolved(device, gateway, selected);
    }

    private boolean isDown(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String type = current.getClass().getName().toLowerCase();
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (type.contains("cannotgetjdbcconnection")
                    || type.contains("connectexception")
                    || type.contains("socketexception")
                    || type.contains("sockettimeoutexception")
                    || type.contains("sqltransientconnection")
                    || type.contains("sqlnontransientconnection")
                    || message.contains("connection") || message.contains("connect") || message.contains("refused")
                    || message.contains("unavailable") || message.contains("timed out") || message.contains("timeout")
                    || message.contains("websocket") || message.contains("network") || message.contains("unreachable")
                    || message.contains("broken pipe") || message.contains("reset") || message.contains("not available")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        String message = error.getMessage();
        while (current.getCause() != null) {
            current = current.getCause();
            if (current.getMessage() != null) {
                message = current.getMessage();
            }
        }
        return message;
    }

    private record Resolved(EmsDevice device, EmsGateway gateway, List<IotProperty> properties) {
    }
}
