package com.ems.admin.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.admin.asset.dto.AssetDtos;
import com.ems.admin.asset.entity.EmsDevice;
import com.ems.admin.asset.entity.EmsGateway;
import com.ems.admin.asset.mapper.EmsDeviceMapper;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.Pages;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.thingmodel.entity.IotProduct;
import com.ems.admin.thingmodel.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DeviceService {
    private final EmsDeviceMapper deviceMapper;
    private final GatewayService gatewayService;
    private final StationGuard stationGuard;
    private final ProductService productService;

    public DeviceService(EmsDeviceMapper deviceMapper, GatewayService gatewayService, StationGuard stationGuard,
                         ProductService productService) {
        this.deviceMapper = deviceMapper;
        this.gatewayService = gatewayService;
        this.stationGuard = stationGuard;
        this.productService = productService;
    }

    public PageResult<AssetDtos.DeviceVO> page(long pageNum, long pageSize, Long stationId, Long gatewayId, String name) {
        LambdaQueryWrapper<EmsDevice> wrapper = new LambdaQueryWrapper<>();
        stationGuard.apply(wrapper, EmsDevice::getStationId);
        if (stationId != null) {
            stationGuard.check(stationId);
            wrapper.eq(EmsDevice::getStationId, stationId);
        }
        if (gatewayId != null) {
            EmsGateway gateway = gatewayService.require(gatewayId);
            stationGuard.check(gateway.getStationId());
            wrapper.eq(EmsDevice::getGatewayId, gatewayId);
        }
        if (StringUtils.hasText(name)) {
            wrapper.like(EmsDevice::getName, name);
        }
        wrapper.orderByDesc(EmsDevice::getId);
        Page<EmsDevice> page = deviceMapper.selectPage(Pages.of(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toVo).toList());
    }

    public AssetDtos.DeviceVO get(Long id) {
        return toVo(require(id));
    }

    @Transactional
    public Long create(AssetDtos.DeviceRequest request) {
        EmsGateway gateway = gatewayService.require(request.gatewayId());
        stationGuard.check(gateway.getStationId());
        assertCodeFree(gateway.getId(), request.deviceCode(), null);
        EmsDevice device = new EmsDevice();
        device.setTenantId(TenantContext.get());
        device.setStationId(gateway.getStationId());
        device.setGatewayId(gateway.getId());
        fill(device, request);
        deviceMapper.insert(device);
        return device.getId();
    }

    @Transactional
    public void update(Long id, AssetDtos.DeviceRequest request) {
        EmsDevice device = require(id);
        EmsGateway gateway = gatewayService.require(request.gatewayId());
        stationGuard.check(gateway.getStationId());
        assertCodeFree(gateway.getId(), request.deviceCode(), id);
        device.setStationId(gateway.getStationId());
        device.setGatewayId(gateway.getId());
        fill(device, request);
        deviceMapper.updateById(device);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        deviceMapper.deleteById(id);
    }

    @Transactional
    public void bindProduct(Long id, Long productId) {
        EmsDevice device = require(id);
        IotProduct product = productService.requirePublishedVisible(productId);
        device.setProductId(product.getId());
        device.setModelVersion(product.getModelVersion());
        deviceMapper.updateById(device);
    }

    public EmsDevice require(Long id) {
        EmsDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw BizException.notFound("设备不存在");
        }
        stationGuard.check(device.getStationId());
        return device;
    }

    private void fill(EmsDevice device, AssetDtos.DeviceRequest request) {
        device.setName(request.name());
        device.setDeviceCode(request.deviceCode());
        device.setSn(request.sn());
        device.setStatus(StringUtils.hasText(request.status()) ? request.status() : "INACTIVE");
    }

    private void assertCodeFree(Long gatewayId, String deviceCode, Long excludeId) {
        LambdaQueryWrapper<EmsDevice> wrapper = new LambdaQueryWrapper<EmsDevice>()
                .eq(EmsDevice::getGatewayId, gatewayId)
                .eq(EmsDevice::getDeviceCode, deviceCode);
        if (excludeId != null) {
            wrapper.ne(EmsDevice::getId, excludeId);
        }
        Long count = deviceMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw BizException.conflict("设备标识已存在");
        }
    }

    private AssetDtos.DeviceVO toVo(EmsDevice device) {
        AssetDtos.DeviceVO vo = new AssetDtos.DeviceVO();
        vo.setId(device.getId());
        vo.setStationId(device.getStationId());
        vo.setGatewayId(device.getGatewayId());
        vo.setName(device.getName());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setProductId(device.getProductId());
        vo.setModelVersion(device.getModelVersion());
        vo.setSn(device.getSn());
        vo.setStatus(device.getStatus());
        vo.setCreatedAt(device.getCreatedAt());
        return vo;
    }
}
