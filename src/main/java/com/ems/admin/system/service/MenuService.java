package com.ems.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.common.TenantIds;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.entity.SysMenu;
import com.ems.admin.system.mapper.SysMenuMapper;
import com.ems.admin.system.mapper.SysRoleMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuService {
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final MenuTreeService menuTreeService;

    public MenuService(SysMenuMapper menuMapper, SysRoleMenuMapper roleMenuMapper, MenuTreeService menuTreeService) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuTreeService = menuTreeService;
    }

    public List<MenuTreeService.MenuNode> tree() {
        return menuTreeService.toTree(menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortNum)));
    }

    @Transactional
    public Long create(SystemDtos.MenuRequest request) {
        assertParent(request.parentId());
        SysMenu menu = new SysMenu();
        fill(menu, request);
        menu.setTenantId(TenantIds.PLATFORM);
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Transactional
    public void update(Long id, SystemDtos.MenuRequest request) {
        SysMenu menu = require(id);
        if (id.equals(request.parentId())) {
            throw BizException.badRequest("上级菜单不能是自己");
        }
        assertParent(request.parentId());
        fill(menu, request);
        menuMapper.updateById(menu);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        Long children = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        if (children != null && children > 0) {
            throw BizException.conflict("请先删除子菜单");
        }
        boolean previous = TenantContext.setIgnore(true);
        try {
            roleMenuMapper.deleteByMenuId(id);
        } finally {
            TenantContext.setIgnore(previous);
        }
        menuMapper.deleteById(id);
    }

    private void fill(SysMenu menu, SystemDtos.MenuRequest request) {
        menu.setParentId(request.parentId());
        menu.setName(request.name());
        menu.setMenuType(request.menuType());
        menu.setPath(request.path());
        menu.setPerms(request.perms());
        menu.setSortNum(request.sortNum());
        menu.setVisible(request.visible() == null ? 1 : request.visible());
    }

    private void assertParent(Long parentId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        if (menuMapper.selectById(parentId) == null) {
            throw BizException.badRequest("上级菜单不存在");
        }
    }

    private SysMenu require(Long id) {
        SysMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw BizException.notFound("菜单不存在");
        }
        return menu;
    }
}
