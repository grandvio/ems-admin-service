package com.ems.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.asset.service.StationGuard;
import com.ems.admin.common.TenantIds;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.Pages;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.security.CurrentUser;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.system.RoleTemplates;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.entity.SysMenu;
import com.ems.admin.system.entity.SysRole;
import com.ems.admin.system.entity.SysRoleMenu;
import com.ems.admin.system.entity.SysRoleStation;
import com.ems.admin.system.entity.SysUserRole;
import com.ems.admin.system.mapper.SysMenuMapper;
import com.ems.admin.system.mapper.SysRoleMapper;
import com.ems.admin.system.mapper.SysRoleMenuMapper;
import com.ems.admin.system.mapper.SysRoleStationMapper;
import com.ems.admin.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleService {
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleStationMapper roleStationMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;
    private final EmsStationMapper stationMapper;
    private final AuthQueryService authQueryService;
    private final StationGuard stationGuard;

    public RoleService(SysRoleMapper roleMapper, SysRoleMenuMapper roleMenuMapper, SysRoleStationMapper roleStationMapper,
                       SysUserRoleMapper userRoleMapper, SysMenuMapper menuMapper, EmsStationMapper stationMapper,
                       AuthQueryService authQueryService, StationGuard stationGuard) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleStationMapper = roleStationMapper;
        this.userRoleMapper = userRoleMapper;
        this.menuMapper = menuMapper;
        this.stationMapper = stationMapper;
        this.authQueryService = authQueryService;
        this.stationGuard = stationGuard;
    }

    public PageResult<SystemDtos.RoleVO> page(long pageNum, long pageSize, String name) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(name)) {
            wrapper.like(SysRole::getName, name);
        }
        wrapper.orderByAsc(SysRole::getId);
        Page<SysRole> page = roleMapper.selectPage(Pages.of(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(role -> toVo(role, false)).toList());
    }

    public SystemDtos.RoleVO get(Long id) {
        return toVo(require(id), true);
    }

    @Transactional
    public Long create(SystemDtos.RoleCreateRequest request) {
        if (RoleTemplates.RESERVED.contains(request.code())) {
            throw BizException.conflict("角色编码为系统保留");
        }
        assertCodeFree(request.code());
        SysRole role = new SysRole();
        role.setTenantId(TenantContext.get());
        role.setCode(request.code());
        role.setName(request.name());
        role.setDataScope(request.dataScope());
        role.setBuiltIn(0);
        role.setRemark(request.remark());
        roleMapper.insert(role);
        return role.getId();
    }

    @Transactional
    public void update(Long id, SystemDtos.RoleUpdateRequest request) {
        SysRole role = require(id);
        role.setName(request.name());
        role.setDataScope(request.dataScope());
        role.setRemark(request.remark());
        roleMapper.updateById(role);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = require(id);
        if (role.getBuiltIn() != null && role.getBuiltIn() == 1) {
            throw BizException.badRequest("内置角色不能删除");
        }
        Long used = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (used != null && used > 0) {
            throw BizException.conflict("角色已分配给用户");
        }
        roleMenuMapper.deleteByRoleId(id);
        roleStationMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
    }

    @Transactional
    public void assignMenus(Long id, List<Long> menuIds) {
        require(id);
        List<Long> distinct = menuIds == null ? List.of() : menuIds.stream().distinct().toList();
        if (!distinct.isEmpty()) {
            List<SysMenu> menus = menuMapper.selectByIds(distinct);
            if (menus.size() != distinct.size()) {
                throw BizException.badRequest("菜单不存在");
            }
            assertCanGrant(menus);
        }
        roleMenuMapper.deleteByRoleId(id);
        for (Long menuId : distinct) {
            SysRoleMenu link = new SysRoleMenu();
            link.setTenantId(TenantContext.get());
            link.setRoleId(id);
            link.setMenuId(menuId);
            roleMenuMapper.insert(link);
        }
    }

    @Transactional
    public void assignStations(Long id, List<Long> stationIds) {
        require(id);
        List<Long> distinct = stationIds == null ? List.of() : stationIds.stream().distinct().toList();
        List<Long> visible = stationGuard.visibleStationIds();
        for (Long stationId : distinct) {
            EmsStation station = stationMapper.selectById(stationId);
            if (station == null) {
                throw BizException.notFound("电站不存在");
            }
            if (visible != null && !visible.contains(stationId)) {
                throw BizException.forbidden("无权分配该电站");
            }
        }
        roleStationMapper.deleteByRoleId(id);
        for (Long stationId : distinct) {
            SysRoleStation link = new SysRoleStation();
            link.setTenantId(TenantContext.get());
            link.setRoleId(id);
            link.setStationId(stationId);
            roleStationMapper.insert(link);
        }
    }

    private void assertCanGrant(List<SysMenu> menus) {
        if (TenantIds.isPlatform(CurrentUser.require().getTenantId())) {
            return;
        }
        Set<Long> own = new HashSet<>(authQueryService.menusOf(CurrentUser.require().getUserId()).stream().map(SysMenu::getId).toList());
        for (SysMenu menu : menus) {
            if (menu.getPerms() != null && (menu.getPerms().startsWith("system:tenant") || menu.getPerms().startsWith("system:menu"))) {
                throw BizException.forbidden("不能分配平台菜单");
            }
            if (!own.contains(menu.getId())) {
                throw BizException.forbidden("不能分配自己没有的菜单");
            }
        }
    }

    private void assertCodeFree(String code) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
        if (count != null && count > 0) {
            throw BizException.conflict("角色编码已存在");
        }
    }

    private SysRole require(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound("角色不存在");
        }
        return role;
    }

    private SystemDtos.RoleVO toVo(SysRole role, boolean detail) {
        SystemDtos.RoleVO vo = new SystemDtos.RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDataScope(role.getDataScope());
        vo.setBuiltIn(role.getBuiltIn());
        vo.setRemark(role.getRemark());
        if (detail) {
            vo.setMenuIds(roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getId()))
                    .stream().map(SysRoleMenu::getMenuId).toList());
            vo.setStationIds(roleStationMapper.selectList(new LambdaQueryWrapper<SysRoleStation>().eq(SysRoleStation::getRoleId, role.getId()))
                    .stream().map(SysRoleStation::getStationId).toList());
        }
        return vo;
    }
}
