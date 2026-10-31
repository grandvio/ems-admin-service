package com.ems.admin.asset.controller;

import com.ems.admin.asset.dto.AssetDtos;
import com.ems.admin.asset.service.GatewayService;
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
@RequestMapping("/api/v1/gateways")
public class GatewayController {
    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.GATEWAY_LIST + "')")
    public R<PageResult<AssetDtos.GatewayVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) Long stationId,
                                                   @RequestParam(required = false) String name) {
        return R.ok(gatewayService.page(pageNum, pageSize, stationId, name));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.GATEWAY_LIST + "')")
    public R<AssetDtos.GatewayVO> get(@PathVariable Long id) {
        return R.ok(gatewayService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Perms.GATEWAY_ADD + "')")
    public R<Long> create(@Valid @RequestBody AssetDtos.GatewayRequest request) {
        return R.ok(gatewayService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.GATEWAY_EDIT + "')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AssetDtos.GatewayRequest request) {
        gatewayService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.GATEWAY_REMOVE + "')")
    public R<Void> delete(@PathVariable Long id) {
        gatewayService.delete(id);
        return R.ok();
    }
}
