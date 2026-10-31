package com.ems.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.framework.security.LoginUser;
import com.ems.admin.system.entity.SysMenu;
import com.ems.admin.system.entity.SysRole;
import com.ems.admin.system.entity.SysRoleMenu;
import com.ems.admin.system.entity.SysUser;
import com.ems.admin.system.entity.SysUserRole;
import com.ems.admin.system.mapper.SysMenuMapper;
import com.ems.admin.system.mapper.SysRoleMapper;
import com.ems.admin.system.mapper.SysRoleMenuMapper;
import com.ems.admin.system.mapper.SysUserMapper;
import com.ems.admin.system.mapper.SysUserRoleMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
public class AuthQueryService {
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;

    public AuthQueryService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                             SysRoleMenuMapper roleMenuMapper, SysMenuMapper menuMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    public Optional<LoginUser> loadUser(Long userId, Long tenantId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !tenantId.equals(user.getTenantId()) || !"ENABLED".equals(user.getStatus())) {
            return Optional.empty();
        }
        List<String> perms = permissionsOf(userId);
        List<SimpleGrantedAuthority> authorities = perms.stream().map(SimpleGrantedAuthority::new).toList();
        return Optional.of(new LoginUser(user.getId(), user.getTenantId(), user.getUsername(), authorities));
    }

    public List<SysRole> rolesOf(Long userId) {
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectByIds(roleIds);
    }

    public List<SysMenu> menusOf(Long userId) {
        List<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectByIds(menuIds);
    }

    public List<String> permissionsOf(Long userId) {
        return menusOf(userId).stream()
                .map(SysMenu::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<Long> roleIdsOf(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).distinct().toList();
    }
}
