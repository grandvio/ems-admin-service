package com.ems.admin.system.controller;

import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.service.RoleService;
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
@RequestMapping("/api/v1/system/roles")
public class RoleController {
    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.ROLE_LIST + "')")
    public R<PageResult<SystemDtos.RoleVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String name) {
        return R.ok(roleService.page(pageNum, pageSize, name));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.ROLE_LIST + "')")
    public R<SystemDtos.RoleVO> get(@PathVariable Long id) {
        return R.ok(roleService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Perms.ROLE_ADD + "')")
    public R<Long> create(@Valid @RequestBody SystemDtos.RoleCreateRequest request) {
        return R.ok(roleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.ROLE_EDIT + "')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SystemDtos.RoleUpdateRequest request) {
        roleService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.ROLE_REMOVE + "')")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('" + Perms.ROLE_EDIT + "')")
    public R<Void> menus(@PathVariable Long id, @Valid @RequestBody SystemDtos.MenuIdsRequest request) {
        roleService.assignMenus(id, request.menuIds());
        return R.ok();
    }

    @PutMapping("/{id}/stations")
    @PreAuthorize("hasAuthority('" + Perms.ROLE_EDIT + "')")
    public R<Void> stations(@PathVariable Long id, @Valid @RequestBody SystemDtos.StationIdsRequest request) {
        roleService.assignStations(id, request.stationIds());
        return R.ok();
    }
}
