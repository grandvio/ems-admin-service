package com.ems.admin.energy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.asset.entity.EmsDevice;
import com.ems.admin.asset.entity.EmsGateway;
import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.asset.mapper.EmsDeviceMapper;
import com.ems.admin.asset.mapper.EmsGatewayMapper;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.energy.entity.EmsEnergyCheckpoint;
import com.ems.admin.energy.entity.EmsMetricEnergyHour;
import com.ems.admin.energy.mapper.EmsEnergyCheckpointMapper;
import com.ems.admin.energy.mapper.EmsMetricEnergyHourMapper;
import com.ems.admin.framework.config.EmsProperties;
import com.ems.admin.tsquery.SubTableNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EnergyRollupService {
    private static final int PAGE = 5000;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final EmsDeviceMapper deviceMapper;
    private final EmsGatewayMapper gatewayMapper;
    private final EmsStationMapper stationMapper;
    private final EmsEnergyCheckpointMapper checkpointMapper;
    private final EmsMetricEnergyHourMapper hourMapper;
    private final CumulativeReader cumulativeReader;
    private final EmsProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public EnergyRollupService(EmsDeviceMapper deviceMapper, EmsGatewayMapper gatewayMapper, EmsStationMapper stationMapper,
                               EmsEnergyCheckpointMapper checkpointMapper, EmsMetricEnergyHourMapper hourMapper,
                               CumulativeReader cumulativeReader, EmsProperties properties,
                               PlatformTransactionManager transactionManager) {
        this.deviceMapper = deviceMapper;
        this.gatewayMapper = gatewayMapper;
        this.stationMapper = stationMapper;
        this.checkpointMapper = checkpointMapper;
        this.hourMapper = hourMapper;
        this.cumulativeReader = cumulativeReader;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void advance(Long deviceId, String metric) {
        String key = deviceId + "\0" + metric;
        Object lock = locks.computeIfAbsent(key, id -> new Object());
        synchronized (lock) {
            try {
                doAdvance(deviceId, metric);
            } catch (RuntimeException ex) {
                log.warn("电量汇总失败 device={} metric={}", deviceId, metric, ex);
            }
        }
    }

    private void doAdvance(Long deviceId, String metric) {
        EmsDevice device = deviceMapper.selectById(deviceId);
        if (device == null || device.getStationId() == null) {
            return;
        }
        EmsGateway gateway = gatewayMapper.selectById(device.getGatewayId());
        EmsStation station = stationMapper.selectById(device.getStationId());
        if (gateway == null || station == null) {
            return;
        }
        ZoneId zone = zone(station.getTimezone());
        Pull pull = pull(device, gateway, properties.getTdengine().getDatabase(), metric, zone);
        if (pull == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            writeHours(device, metric, pull);
            saveCheckpoint(device, metric, pull);
        });
    }

    private Pull pull(EmsDevice device, EmsGateway gateway, String database, String metric, ZoneId zone) {
        EmsEnergyCheckpoint checkpoint = checkpointMapper.selectOne(new LambdaQueryWrapper<EmsEnergyCheckpoint>()
                .eq(EmsEnergyCheckpoint::getDeviceId, device.getId())
                .eq(EmsEnergyCheckpoint::getMetricName, metric));
        Long cursor = checkpoint == null ? null : checkpoint.getLastEpochMs();
        Double previous = checkpoint == null || checkpoint.getLastValue() == null ? null : checkpoint.getLastValue().doubleValue();
        List<EnergyDeltas.Slice> slices = new ArrayList<>();
        boolean advanced = false;
        String table = SubTableNames.qualified(database, gateway.getGroupId(), gateway.getEdgeNodeId(), device.getDeviceCode(), metric);
        try {
            while (true) {
                long cursorBefore = cursor == null ? Long.MIN_VALUE : cursor;
                List<CumulativeReader.Sample> batch = cumulativeReader.readAfter(table, cursor, PAGE);
                if (batch.isEmpty()) {
                    break;
                }
                advanced = true;
                List<EnergyDeltas.Reading> valued = new ArrayList<>();
                for (CumulativeReader.Sample sample : batch) {
                    cursor = sample.epochMilli();
                    if (sample.value() != null) {
                        valued.add(new EnergyDeltas.Reading(sample.epochMilli(), sample.value()));
                    }
                }
                if (cursor != null && cursor <= cursorBefore) {
                    break;
                }
                slices.addAll(EnergyDeltas.fold(previous, valued, zone));
                if (!valued.isEmpty()) {
                    previous = valued.get(valued.size() - 1).value();
                }
                if (batch.size() < PAGE) {
                    break;
                }
            }
        } catch (DataAccessException ex) {
            log.warn("读取累计电能失败 device={} metric={} msg={}", device.getId(), metric, ex.getMessage());
            return null;
        }
        return new Pull(slices, advanced, cursor, previous);
    }

    private void writeHours(EmsDevice device, String metric, Pull pull) {
        if (!pull.advanced() || pull.lastValue() == null) {
            return;
        }
        for (Map.Entry<LocalDateTime, Double> entry : EnergyDeltas.sumByHour(pull.slices()).entrySet()) {
            addHour(device, metric, entry.getKey(), entry.getValue());
        }
    }

    private void addHour(EmsDevice device, String metric, LocalDateTime hourStart, double kwh) {
        BigDecimal add = BigDecimal.valueOf(kwh).setScale(6, RoundingMode.HALF_UP);
        EmsMetricEnergyHour row = hourMapper.selectOne(new LambdaQueryWrapper<EmsMetricEnergyHour>()
                .eq(EmsMetricEnergyHour::getDeviceId, device.getId())
                .eq(EmsMetricEnergyHour::getMetricName, metric)
                .eq(EmsMetricEnergyHour::getHourStart, hourStart));
        if (row == null) {
            row = new EmsMetricEnergyHour();
            row.setTenantId(device.getTenantId());
            row.setStationId(device.getStationId());
            row.setDeviceId(device.getId());
            row.setMetricName(metric);
            row.setHourStart(hourStart);
            row.setKwh(add);
            hourMapper.insert(row);
            return;
        }
        row.setKwh(nullToZero(row.getKwh()).add(add));
        hourMapper.updateById(row);
    }

    private void saveCheckpoint(EmsDevice device, String metric, Pull pull) {
        if (!pull.advanced() || pull.cursor() == null || pull.lastValue() == null) {
            return;
        }
        EmsEnergyCheckpoint checkpoint = checkpointMapper.selectOne(new LambdaQueryWrapper<EmsEnergyCheckpoint>()
                .eq(EmsEnergyCheckpoint::getDeviceId, device.getId())
                .eq(EmsEnergyCheckpoint::getMetricName, metric));
        if (checkpoint == null) {
            checkpoint = new EmsEnergyCheckpoint();
            checkpoint.setTenantId(device.getTenantId());
            checkpoint.setDeviceId(device.getId());
            checkpoint.setMetricName(metric);
            checkpoint.setLastEpochMs(pull.cursor());
            checkpoint.setLastValue(BigDecimal.valueOf(pull.lastValue()).setScale(6, RoundingMode.HALF_UP));
            checkpointMapper.insert(checkpoint);
            return;
        }
        checkpoint.setLastEpochMs(pull.cursor());
        checkpoint.setLastValue(BigDecimal.valueOf(pull.lastValue()).setScale(6, RoundingMode.HALF_UP));
        checkpointMapper.updateById(checkpoint);
    }

    private ZoneId zone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record Pull(List<EnergyDeltas.Slice> slices, boolean advanced, Long cursor, Double lastValue) {
    }
}
