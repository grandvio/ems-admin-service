package com.ems.admin.system.controller;

import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.service.MenuService;
import com.ems.admin.system.service.MenuTreeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/menus")
public class MenuController {
    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.MENU_LIST + "')")
    public R<List<MenuTreeService.MenuNode>> tree() {
        return R.ok(menuService.tree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Perms.MENU_ADD + "')")
    public R<Long> create(@Valid @RequestBody SystemDtos.MenuRequest request) {
        return R.ok(menuService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.MENU_EDIT + "')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SystemDtos.MenuRequest request) {
        menuService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.MENU_REMOVE + "')")
    public R<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return R.ok();
    }
}
