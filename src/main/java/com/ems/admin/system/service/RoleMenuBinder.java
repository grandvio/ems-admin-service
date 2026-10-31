package com.ems.admin.system.service;

import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.system.entity.SysMenu;
import com.ems.admin.system.entity.SysRoleMenu;
import com.ems.admin.system.mapper.SysMenuMapper;
import com.ems.admin.system.mapper.SysRoleMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoleMenuBinder {
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public RoleMenuBinder(SysMenuMapper menuMapper, SysRoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Transactional
    public void bindAll(Long roleId) {
        replace(roleId, menuMapper.selectList(null).stream().map(SysMenu::getId).toList());
    }

    @Transactional
    public void bindByPerms(Long roleId, Set<String> perms) {
        List<SysMenu> all = menuMapper.selectList(null);
        Map<Long, SysMenu> byId = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (SysMenu menu : all) {
            byId.put(menu.getId(), menu);
            if (menu.getPerms() != null && perms.contains(menu.getPerms())) {
                ids.add(menu.getId());
            }
        }
        boolean grow = true;
        while (grow) {
            grow = false;
            for (Long id : new ArrayList<>(ids)) {
                SysMenu menu = byId.get(id);
                if (menu == null || menu.getParentId() == null || menu.getParentId() == 0) {
                    continue;
                }
                if (ids.add(menu.getParentId())) {
                    grow = true;
                }
            }
        }
        replace(roleId, ids);
    }

    private void replace(Long roleId, Iterable<Long> menuIds) {
        roleMenuMapper.deleteByRoleId(roleId);
        Long tenantId = TenantContext.get();
        for (Long menuId : menuIds) {
            SysRoleMenu link = new SysRoleMenu();
            link.setTenantId(tenantId);
            link.setRoleId(roleId);
            link.setMenuId(menuId);
            roleMenuMapper.insert(link);
        }
    }
}
