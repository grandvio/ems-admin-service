package com.ems.admin.system.controller;

import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.service.TenantService;
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
@RequestMapping("/api/v1/system/tenants")
public class TenantController {
    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.TENANT_LIST + "')")
    public R<PageResult<SystemDtos.TenantVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) String name) {
        return R.ok(tenantService.page(pageNum, pageSize, name));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.TENANT_LIST + "')")
    public R<SystemDtos.TenantVO> get(@PathVariable Long id) {
        return R.ok(tenantService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Perms.TENANT_ADD + "')")
    public R<Long> create(@Valid @RequestBody SystemDtos.TenantCreateRequest request) {
        return R.ok(tenantService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.TENANT_EDIT + "')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SystemDtos.TenantUpdateRequest request) {
        tenantService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.TENANT_REMOVE + "')")
    public R<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return R.ok();
    }
}
