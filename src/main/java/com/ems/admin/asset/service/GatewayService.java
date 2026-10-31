package com.ems.admin.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.admin.asset.dto.AssetDtos;
import com.ems.admin.asset.entity.EmsDevice;
import com.ems.admin.asset.entity.EmsGateway;
import com.ems.admin.asset.mapper.EmsDeviceMapper;
import com.ems.admin.asset.mapper.EmsGatewayMapper;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.Pages;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GatewayService {
    private final EmsGatewayMapper gatewayMapper;
    private final EmsDeviceMapper deviceMapper;
    private final StationGuard stationGuard;

    public GatewayService(EmsGatewayMapper gatewayMapper, EmsDeviceMapper deviceMapper, StationGuard stationGuard) {
        this.gatewayMapper = gatewayMapper;
        this.deviceMapper = deviceMapper;
        this.stationGuard = stationGuard;
    }

    public PageResult<AssetDtos.GatewayVO> page(long pageNum, long pageSize, Long stationId, String name) {
        LambdaQueryWrapper<EmsGateway> wrapper = new LambdaQueryWrapper<>();
        stationGuard.apply(wrapper, EmsGateway::getStationId);
        if (stationId != null) {
            stationGuard.check(stationId);
            wrapper.eq(EmsGateway::getStationId, stationId);
        }
        if (StringUtils.hasText(name)) {
            wrapper.like(EmsGateway::getName, name);
        }
        wrapper.orderByDesc(EmsGateway::getId);
        Page<EmsGateway> page = gatewayMapper.selectPage(Pages.of(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toVo).toList());
    }

    public AssetDtos.GatewayVO get(Long id) {
        EmsGateway gateway = require(id);
        stationGuard.check(gateway.getStationId());
        return toVo(gateway);
    }

    @Transactional
    public Long create(AssetDtos.GatewayRequest request) {
        stationGuard.check(request.stationId());
        assertIdentityFree(request.groupId(), request.edgeNodeId(), null);
        EmsGateway gateway = new EmsGateway();
        gateway.setTenantId(TenantContext.get());
        fill(gateway, request);
        gatewayMapper.insert(gateway);
        return gateway.getId();
    }

    @Transactional
    public void update(Long id, AssetDtos.GatewayRequest request) {
        EmsGateway gateway = require(id);
        stationGuard.check(gateway.getStationId());
        stationGuard.check(request.stationId());
        assertIdentityFree(request.groupId(), request.edgeNodeId(), id);
        fill(gateway, request);
        gatewayMapper.updateById(gateway);
    }

    @Transactional
    public void delete(Long id) {
        EmsGateway gateway = require(id);
        stationGuard.check(gateway.getStationId());
        Long devices = deviceMapper.selectCount(new LambdaQueryWrapper<EmsDevice>().eq(EmsDevice::getGatewayId, id));
        if (devices != null && devices > 0) {
            throw BizException.conflict("请先删除网关下的设备");
        }
        gatewayMapper.deleteById(id);
    }

    public EmsGateway require(Long id) {
        EmsGateway gateway = gatewayMapper.selectById(id);
        if (gateway == null) {
            throw BizException.notFound("网关不存在");
        }
        return gateway;
    }

    private void fill(EmsGateway gateway, AssetDtos.GatewayRequest request) {
        gateway.setStationId(request.stationId());
        gateway.setName(request.name());
        gateway.setGroupId(request.groupId());
        gateway.setEdgeNodeId(request.edgeNodeId());
        gateway.setOnlineStatus(StringUtils.hasText(request.onlineStatus()) ? request.onlineStatus() : "OFFLINE");
    }

    private void assertIdentityFree(String groupId, String edgeNodeId, Long excludeId) {
        boolean previous = TenantContext.setIgnore(true);
        try {
            LambdaQueryWrapper<EmsGateway> wrapper = new LambdaQueryWrapper<EmsGateway>()
                    .eq(EmsGateway::getGroupId, groupId)
                    .eq(EmsGateway::getEdgeNodeId, edgeNodeId);
            if (excludeId != null) {
                wrapper.ne(EmsGateway::getId, excludeId);
            }
            Long count = gatewayMapper.selectCount(wrapper);
            if (count != null && count > 0) {
                throw BizException.conflict("该 Sparkplug 网关标识已被占用");
            }
        } finally {
            TenantContext.setIgnore(previous);
        }
    }

    private AssetDtos.GatewayVO toVo(EmsGateway gateway) {
        AssetDtos.GatewayVO vo = new AssetDtos.GatewayVO();
        vo.setId(gateway.getId());
        vo.setStationId(gateway.getStationId());
        vo.setName(gateway.getName());
        vo.setGroupId(gateway.getGroupId());
        vo.setEdgeNodeId(gateway.getEdgeNodeId());
        vo.setOnlineStatus(gateway.getOnlineStatus());
        vo.setLastBirthAt(gateway.getLastBirthAt());
        vo.setLastDeathAt(gateway.getLastDeathAt());
        vo.setCreatedAt(gateway.getCreatedAt());
        return vo;
    }
}
