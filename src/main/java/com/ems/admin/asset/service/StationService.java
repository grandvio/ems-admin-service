package com.ems.admin.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.admin.asset.dto.AssetDtos;
import com.ems.admin.asset.entity.EmsGateway;
import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.asset.mapper.EmsGatewayMapper;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.Pages;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.oss.service.StationImageService;
import com.ems.admin.system.mapper.SysRoleStationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
public class StationService {
    private final EmsStationMapper stationMapper;
    private final EmsGatewayMapper gatewayMapper;
    private final SysRoleStationMapper roleStationMapper;
    private final StationGuard stationGuard;
    private final StationImageService stationImageService;

    public StationService(EmsStationMapper stationMapper, EmsGatewayMapper gatewayMapper,
                          SysRoleStationMapper roleStationMapper, StationGuard stationGuard,
                          StationImageService stationImageService) {
        this.stationMapper = stationMapper;
        this.gatewayMapper = gatewayMapper;
        this.roleStationMapper = roleStationMapper;
        this.stationGuard = stationGuard;
        this.stationImageService = stationImageService;
    }

    public PageResult<AssetDtos.StationVO> page(long pageNum, long pageSize, String name, String stationType, String status) {
        LambdaQueryWrapper<EmsStation> wrapper = new LambdaQueryWrapper<>();
        stationGuard.apply(wrapper, EmsStation::getId);
        if (StringUtils.hasText(name)) {
            wrapper.like(EmsStation::getName, name);
        }
        if (StringUtils.hasText(stationType)) {
            wrapper.eq(EmsStation::getStationType, stationType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(EmsStation::getStatus, status);
        }
        wrapper.orderByDesc(EmsStation::getId);
        Page<EmsStation> page = stationMapper.selectPage(Pages.of(pageNum, pageSize), wrapper);
        List<AssetDtos.StationVO> rows = page.getRecords().stream().map(this::toVo).toList();
        Map<Long, String> covers = stationImageService.coverUrls(rows.stream().map(AssetDtos.StationVO::getId).toList());
        rows.forEach(row -> row.setCoverUrl(covers.get(row.getId())));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), rows);
    }

    public AssetDtos.StationVO get(Long id) {
        stationGuard.check(id);
        AssetDtos.StationVO vo = toVo(require(id));
        vo.setCoverUrl(stationImageService.coverUrl(id));
        return vo;
    }

    @Transactional
    public Long create(AssetDtos.StationRequest request) {
        assertCodeFree(request.code(), null);
        EmsStation station = new EmsStation();
        station.setTenantId(TenantContext.get());
        fill(station, request);
        stationMapper.insert(station);
        return station.getId();
    }

    @Transactional
    public void update(Long id, AssetDtos.StationRequest request) {
        stationGuard.check(id);
        EmsStation station = require(id);
        assertCodeFree(request.code(), id);
        fill(station, request);
        stationMapper.updateById(station);
    }

    @Transactional
    public void delete(Long id) {
        stationGuard.check(id);
        Long gateways = gatewayMapper.selectCount(new LambdaQueryWrapper<EmsGateway>().eq(EmsGateway::getStationId, id));
        if (gateways != null && gateways > 0) {
            throw BizException.conflict("请先删除电站下的网关");
        }
        roleStationMapper.deleteByStationId(id);
        stationImageService.detach(id);
        stationMapper.deleteById(id);
    }

    private void fill(EmsStation station, AssetDtos.StationRequest request) {
        station.setCode(request.code());
        station.setName(request.name());
        station.setStationType(request.stationType());
        station.setAddress(request.address());
        station.setLongitude(request.longitude());
        station.setLatitude(request.latitude());
        station.setTimezone(StringUtils.hasText(request.timezone()) ? request.timezone() : "Asia/Shanghai");
        station.setCapacityKw(request.capacityKw());
        station.setStorageKwh(request.storageKwh());
        station.setGridConnectedDate(request.gridConnectedDate());
        station.setStatus(request.status());
    }

    private void assertCodeFree(String code, Long excludeId) {
        LambdaQueryWrapper<EmsStation> wrapper = new LambdaQueryWrapper<EmsStation>().eq(EmsStation::getCode, code);
        if (excludeId != null) {
            wrapper.ne(EmsStation::getId, excludeId);
        }
        Long count = stationMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw BizException.conflict("电站编码已存在");
        }
    }

    private EmsStation require(Long id) {
        EmsStation station = stationMapper.selectById(id);
        if (station == null) {
            throw BizException.notFound("电站不存在");
        }
        return station;
    }

    private AssetDtos.StationVO toVo(EmsStation station) {
        AssetDtos.StationVO vo = new AssetDtos.StationVO();
        vo.setId(station.getId());
        vo.setTenantId(station.getTenantId());
        vo.setCode(station.getCode());
        vo.setName(station.getName());
        vo.setStationType(station.getStationType());
        vo.setAddress(station.getAddress());
        vo.setLongitude(station.getLongitude());
        vo.setLatitude(station.getLatitude());
        vo.setTimezone(station.getTimezone());
        vo.setCapacityKw(station.getCapacityKw());
        vo.setStorageKwh(station.getStorageKwh());
        vo.setGridConnectedDate(station.getGridConnectedDate());
        vo.setStatus(station.getStatus());
        vo.setCreatedAt(station.getCreatedAt());
        return vo;
    }
}
