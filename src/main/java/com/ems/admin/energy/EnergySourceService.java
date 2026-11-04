package com.ems.admin.energy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.asset.entity.EmsDevice;
import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.asset.mapper.EmsDeviceMapper;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.asset.service.StationGuard;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.common.tx.AfterCommit;
import com.ems.admin.energy.entity.EmsEnergyCheckpoint;
import com.ems.admin.energy.entity.EmsEnergySource;
import com.ems.admin.energy.entity.EmsMetricEnergyHour;
import com.ems.admin.energy.mapper.EmsEnergyCheckpointMapper;
import com.ems.admin.energy.mapper.EmsEnergySourceMapper;
import com.ems.admin.energy.mapper.EmsMetricEnergyHourMapper;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.thingmodel.entity.IotProperty;
import com.ems.admin.thingmodel.mapper.IotPropertyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EnergySourceService {
    private final EmsStationMapper stationMapper;
    private final EmsDeviceMapper deviceMapper;
    private final EmsEnergySourceMapper sourceMapper;
    private final EmsEnergyCheckpointMapper checkpointMapper;
    private final EmsMetricEnergyHourMapper hourMapper;
    private final IotPropertyMapper propertyMapper;
    private final StationGuard stationGuard;
    private final EnergyRollupService energyRollupService;
    private final AfterCommit afterCommit;

    public EnergySourceService(EmsStationMapper stationMapper, EmsDeviceMapper deviceMapper,
                               EmsEnergySourceMapper sourceMapper, EmsEnergyCheckpointMapper checkpointMapper,
                               EmsMetricEnergyHourMapper hourMapper, IotPropertyMapper propertyMapper,
                               StationGuard stationGuard, EnergyRollupService energyRollupService,
                               AfterCommit afterCommit) {
        this.stationMapper = stationMapper;
        this.deviceMapper = deviceMapper;
        this.sourceMapper = sourceMapper;
        this.checkpointMapper = checkpointMapper;
        this.hourMapper = hourMapper;
        this.propertyMapper = propertyMapper;
        this.stationGuard = stationGuard;
        this.energyRollupService = energyRollupService;
        this.afterCommit = afterCommit;
    }

    public EnergyDtos.SourceBoard list(Long stationId) {
        requireStation(stationId);
        List<EmsEnergySource> sources = sourcesOf(stationId);
        Map<Long, EmsDevice> devices = devicesOf(sources);
        Map<Long, Map<String, IotProperty>> properties = propertiesOf(devices.values());
        EnergyDtos.SourceBoard board = new EnergyDtos.SourceBoard();
        Map<String, List<EnergyDtos.SourceItem>> grouped = new LinkedHashMap<>();
        for (String series : EnergySeries.ALL) {
            grouped.put(series, new ArrayList<>());
        }
        for (EmsEnergySource source : sources) {
            EnergyDtos.SourceItem item = new EnergyDtos.SourceItem();
            item.setId(source.getId());
            item.setDeviceId(source.getDeviceId());
            EmsDevice device = devices.get(source.getDeviceId());
            item.setDeviceName(device == null ? null : device.getName());
            item.setMetricName(source.getMetricName());
            item.setMetricLabel(label(device, properties, source.getMetricName()));
            grouped.computeIfAbsent(source.getSeriesCode(), key -> new ArrayList<>()).add(item);
        }
        board.setCharge(grouped.get(EnergySeries.CHARGE));
        board.setDischarge(grouped.get(EnergySeries.DISCHARGE));
        board.setGeneration(grouped.get(EnergySeries.GENERATION));
        board.setConsumption(grouped.get(EnergySeries.CONSUMPTION));
        board.setImportEnergy(grouped.get(EnergySeries.IMPORT));
        board.setExportEnergy(grouped.get(EnergySeries.EXPORT));
        return board;
    }

    public List<EmsEnergySource> sourcesOf(Long stationId) {
        return sourceMapper.selectList(new LambdaQueryWrapper<EmsEnergySource>()
                .eq(EmsEnergySource::getStationId, stationId)
                .orderByAsc(EmsEnergySource::getId));
    }

    @Transactional
    public void replace(Long stationId, String series, List<EnergyDtos.SourceInput> sources) {
        requireStation(stationId);
        if (!EnergySeries.contains(series)) {
            throw BizException.badRequest("电量项只能是 charge、discharge、generation、consumption、importEnergy、exportEnergy");
        }
        List<EnergyDtos.SourceInput> incoming = sources == null ? List.of() : sources;
        Set<String> seen = new HashSet<>();
        List<CheckedSource> checked = new ArrayList<>();
        for (EnergyDtos.SourceInput input : incoming) {
            String key = input.deviceId() + "\0" + input.metricName();
            if (!seen.add(key)) {
                throw BizException.badRequest("同一个点位不能重复配置");
            }
            checked.add(check(stationId, input));
        }
        assertFree(stationId, series, checked);
        List<EmsEnergySource> current = sourceMapper.selectList(new LambdaQueryWrapper<EmsEnergySource>()
                .eq(EmsEnergySource::getStationId, stationId)
                .eq(EmsEnergySource::getSeriesCode, series));
        Set<String> keep = new HashSet<>();
        for (CheckedSource source : checked) {
            keep.add(source.key());
        }
        for (EmsEnergySource source : current) {
            if (!keep.contains(source.getDeviceId() + "\0" + source.getMetricName())) {
                sourceMapper.deletePhysicalById(source.getId());
            }
        }
        Set<String> existing = new HashSet<>();
        for (EmsEnergySource source : current) {
            existing.add(source.getDeviceId() + "\0" + source.getMetricName());
        }
        List<CheckedSource> created = new ArrayList<>();
        for (CheckedSource source : checked) {
            if (existing.contains(source.key())) {
                continue;
            }
            EmsEnergySource row = new EmsEnergySource();
            row.setTenantId(source.device().getTenantId() == null ? TenantContext.get() : source.device().getTenantId());
            row.setStationId(stationId);
            row.setSeriesCode(series);
            row.setDeviceId(source.device().getId());
            row.setMetricName(source.metricName());
            sourceMapper.insert(row);
            created.add(source);
        }
        if (!created.isEmpty()) {
            afterCommit.run(() -> {
                for (CheckedSource source : created) {
                    if (!hasCheckpoint(source.device().getId(), source.metricName())) {
                        energyRollupService.advance(source.device().getId(), source.metricName());
                    }
                }
            });
        }
    }

    @Transactional
    public void remove(Long stationId, Long sourceId) {
        requireStation(stationId);
        EmsEnergySource source = sourceMapper.selectById(sourceId);
        if (source == null || !stationId.equals(source.getStationId())) {
            throw BizException.notFound("电量来源不存在");
        }
        sourceMapper.deletePhysicalById(sourceId);
    }

    @Transactional
    public void purge(Long deviceId) {
        sourceMapper.deletePhysicalByDeviceId(deviceId);
        checkpointMapper.delete(new LambdaQueryWrapper<EmsEnergyCheckpoint>().eq(EmsEnergyCheckpoint::getDeviceId, deviceId));
        hourMapper.delete(new LambdaQueryWrapper<EmsMetricEnergyHour>().eq(EmsMetricEnergyHour::getDeviceId, deviceId));
    }

    private CheckedSource check(Long stationId, EnergyDtos.SourceInput input) {
        EmsDevice device = deviceMapper.selectById(input.deviceId());
        if (device == null || !stationId.equals(device.getStationId())) {
            throw BizException.badRequest("设备不属于该电站");
        }
        if (device.getProductId() == null) {
            throw BizException.badRequest("请先绑定物模型");
        }
        IotProperty property = propertyMapper.selectOne(new LambdaQueryWrapper<IotProperty>()
                .eq(IotProperty::getProductId, device.getProductId())
                .eq(IotProperty::getIdentifier, input.metricName()));
        if (property == null) {
            throw BizException.badRequest("设备物模型中没有该点位");
        }
        if (!"float".equals(property.getDataType()) && !"int".equals(property.getDataType())) {
            throw BizException.badRequest("点位必须是数值");
        }
        if (property.getUnit() == null || !"kwh".equals(property.getUnit().trim().toLowerCase())) {
            throw BizException.badRequest("点位单位必须是 kWh");
        }
        return new CheckedSource(device, input.metricName());
    }

    private void assertFree(Long stationId, String series, List<CheckedSource> checked) {
        if (checked.isEmpty()) {
            return;
        }
        List<EmsEnergySource> others = sourceMapper.selectList(new LambdaQueryWrapper<EmsEnergySource>()
                .eq(EmsEnergySource::getStationId, stationId)
                .ne(EmsEnergySource::getSeriesCode, series));
        Map<String, String> occupiedSeries = new java.util.HashMap<>();
        for (EmsEnergySource other : others) {
            occupiedSeries.put(other.getDeviceId() + "\0" + other.getMetricName(), other.getSeriesCode());
        }
        for (CheckedSource source : checked) {
            String seriesCode = occupiedSeries.get(source.key());
            if (seriesCode != null) {
                throw BizException.badRequest("该点位已用于" + EnergySeries.label(seriesCode));
            }
        }
    }

    private boolean hasCheckpoint(Long deviceId, String metricName) {
        Long count = checkpointMapper.selectCount(new LambdaQueryWrapper<EmsEnergyCheckpoint>()
                .eq(EmsEnergyCheckpoint::getDeviceId, deviceId)
                .eq(EmsEnergyCheckpoint::getMetricName, metricName));
        return count != null && count > 0;
    }

    private void requireStation(Long stationId) {
        stationGuard.check(stationId);
        EmsStation station = stationMapper.selectById(stationId);
        if (station == null) {
            throw BizException.notFound("电站不存在");
        }
    }

    private Map<Long, EmsDevice> devicesOf(List<EmsEnergySource> sources) {
        List<Long> deviceIds = sources.stream().map(EmsEnergySource::getDeviceId).distinct().toList();
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        return deviceMapper.selectList(new LambdaQueryWrapper<EmsDevice>().in(EmsDevice::getId, deviceIds))
                .stream().collect(java.util.stream.Collectors.toMap(EmsDevice::getId, device -> device, (left, right) -> left));
    }

    private Map<Long, Map<String, IotProperty>> propertiesOf(Iterable<EmsDevice> devices) {
        List<Long> productIds = new ArrayList<>();
        for (EmsDevice device : devices) {
            if (device.getProductId() != null) {
                productIds.add(device.getProductId());
            }
        }
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, IotProperty>> grouped = new java.util.HashMap<>();
        for (IotProperty property : propertyMapper.selectList(new LambdaQueryWrapper<IotProperty>()
                .in(IotProperty::getProductId, productIds))) {
            grouped.computeIfAbsent(property.getProductId(), key -> new java.util.HashMap<>())
                    .putIfAbsent(property.getIdentifier(), property);
        }
        return grouped;
    }

    private String label(EmsDevice device, Map<Long, Map<String, IotProperty>> properties, String metricName) {
        if (device == null || device.getProductId() == null) {
            return null;
        }
        Map<String, IotProperty> ofProduct = properties.get(device.getProductId());
        if (ofProduct == null || ofProduct.get(metricName) == null) {
            return null;
        }
        return ofProduct.get(metricName).getName();
    }

    private record CheckedSource(EmsDevice device, String metricName) {
        String key() {
            return device.getId() + "\0" + metricName;
        }
    }
}
