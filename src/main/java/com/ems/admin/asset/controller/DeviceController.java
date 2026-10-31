package com.ems.admin.asset.controller;

import com.ems.admin.asset.dto.AssetDtos;
import com.ems.admin.asset.service.DeviceService;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.DEVICE_LIST + "')")
    public R<PageResult<AssetDtos.DeviceVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  @RequestParam(required = false) Long stationId,
                                                  @RequestParam(required = false) Long gatewayId,
                                                  @RequestParam(required = false) String name) {
        return R.ok(deviceService.page(pageNum, pageSize, stationId, gatewayId, name));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.DEVICE_LIST + "')")
    public R<AssetDtos.DeviceVO> get(@PathVariable Long id) {
        return R.ok(deviceService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Perms.DEVICE_ADD + "')")
    public R<Long> create(@Valid @RequestBody AssetDtos.DeviceRequest request) {
        return R.ok(deviceService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.DEVICE_EDIT + "')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AssetDtos.DeviceRequest request) {
        deviceService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.DEVICE_REMOVE + "')")
    public R<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/product")
    @PreAuthorize("hasAuthority('" + Perms.DEVICE_EDIT + "')")
    public R<Void> bindProduct(@PathVariable Long id, @Valid @RequestBody AssetDtos.BindProductRequest request) {
        deviceService.bindProduct(id, request.productId());
        return R.ok();
    }
}
