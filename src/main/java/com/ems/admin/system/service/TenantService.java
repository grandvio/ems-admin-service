package com.ems.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.common.TenantIds;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.Pages;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.system.RoleTemplates;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.entity.SysTenant;
import com.ems.admin.system.entity.SysUser;
import com.ems.admin.system.entity.SysUserRole;
import com.ems.admin.system.entity.SysRole;
import com.ems.admin.system.mapper.SysRoleMapper;
import com.ems.admin.system.mapper.SysRoleMenuMapper;
import com.ems.admin.system.mapper.SysRoleStationMapper;
import com.ems.admin.system.mapper.SysTenantMapper;
import com.ems.admin.system.mapper.SysUserMapper;
import com.ems.admin.system.mapper.SysUserRoleMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class TenantService {
    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleStationMapper roleStationMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final EmsStationMapper stationMapper;
    private final RoleMenuBinder roleMenuBinder;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public TenantService(SysTenantMapper tenantMapper, SysUserMapper userMapper, SysRoleMapper roleMapper,
                         SysRoleMenuMapper roleMenuMapper, SysRoleStationMapper roleStationMapper,
                         SysUserRoleMapper userRoleMapper, EmsStationMapper stationMapper, RoleMenuBinder roleMenuBinder,
                         PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.tenantMapper = tenantMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.roleStationMapper = roleStationMapper;
        this.userRoleMapper = userRoleMapper;
        this.stationMapper = stationMapper;
        this.roleMenuBinder = roleMenuBinder;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResult<SystemDtos.TenantVO> page(long pageNum, long pageSize, String name) {
        LambdaQueryWrapper<SysTenant> wrapper = new LambdaQueryWrapper<SysTenant>().ne(SysTenant::getId, TenantIds.PLATFORM);
        if (StringUtils.hasText(name)) {
            wrapper.like(SysTenant::getName, name);
        }
        wrapper.orderByDesc(SysTenant::getId);
        Page<SysTenant> page = tenantMapper.selectPage(Pages.of(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toVo).toList());
    }

    public SystemDtos.TenantVO get(Long id) {
        return toVo(require(id));
    }

    @Transactional
    public Long create(SystemDtos.TenantCreateRequest request) {
        Long previousTenant = TenantContext.get();
        boolean previousIgnore = TenantContext.setIgnore(true);
        try {
            Long codeCount = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getCode, request.code()));
            if (codeCount != null && codeCount > 0) {
                throw BizException.conflict("租户编码已存在");
            }
            Long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.adminUsername()));
            if (userCount != null && userCount > 0) {
                throw BizException.conflict("管理员账号已存在");
            }
            SysTenant tenant = new SysTenant();
            tenant.setName(request.name());
            tenant.setCode(request.code());
            tenant.setContact(request.contact());
            tenant.setStatus("ENABLED");
            tenantMapper.insert(tenant);
            jdbcTemplate.update("UPDATE sys_tenant SET tenant_id = ? WHERE id = ?", tenant.getId(), tenant.getId());

            TenantContext.setIgnore(false);
            TenantContext.set(tenant.getId());
            createBuiltInRoles();
            SysUser admin = new SysUser();
            admin.setTenantId(tenant.getId());
            admin.setUsername(request.adminUsername());
            admin.setPassword(passwordEncoder.encode(request.adminPassword()));
            admin.setNickname(StringUtils.hasText(request.adminNickname()) ? request.adminNickname() : request.name());
            admin.setStatus("ENABLED");
            userMapper.insert(admin);
            SysRole tenantAdmin = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, RoleTemplates.TENANT_ADMIN));
            SysUserRole link = new SysUserRole();
            link.setTenantId(tenant.getId());
            link.setUserId(admin.getId());
            link.setRoleId(tenantAdmin.getId());
            userRoleMapper.insert(link);
            return tenant.getId();
        } finally {
            TenantContext.setIgnore(previousIgnore);
            TenantContext.set(previousTenant);
        }
    }

    @Transactional
    public void update(Long id, SystemDtos.TenantUpdateRequest request) {
        SysTenant tenant = require(id);
        if (TenantIds.isPlatform(id)) {
            throw BizException.badRequest("不能修改平台租户");
        }
        tenant.setName(request.name());
        tenant.setContact(request.contact());
        tenant.setStatus(request.status());
        tenantMapper.updateById(tenant);
    }

    @Transactional
    public void delete(Long id) {
        if (TenantIds.isPlatform(id)) {
            throw BizException.badRequest("不能删除平台租户");
        }
        require(id);
        Long previous = TenantContext.get();
        try {
            TenantContext.set(id);
            Long stations = stationMapper.selectCount(null);
            if (stations != null && stations > 0) {
                throw BizException.conflict("请先删除租户下的电站");
            }
            for (SysUser user : userMapper.selectList(null)) {
                userRoleMapper.deleteByUserId(user.getId());
                userMapper.deleteById(user.getId());
            }
            for (SysRole role : roleMapper.selectList(null)) {
                roleMenuMapper.deleteByRoleId(role.getId());
                roleStationMapper.deleteByRoleId(role.getId());
                roleMapper.deleteById(role.getId());
            }
        } finally {
            TenantContext.set(previous);
        }
        tenantMapper.deleteById(id);
    }

    public void createBuiltInRoles() {
        SysRole admin = role(RoleTemplates.TENANT_ADMIN, "租户管理员", "ALL");
        roleMenuBinder.bindByPerms(admin.getId(), RoleTemplates.tenantAdmin());
        SysRole stationAdmin = role(RoleTemplates.STATION_ADMIN, "电站管理员", "CUSTOM");
        roleMenuBinder.bindByPerms(stationAdmin.getId(), RoleTemplates.stationAdmin());
        SysRole operator = role(RoleTemplates.OPERATOR, "运维", "CUSTOM");
        roleMenuBinder.bindByPerms(operator.getId(), RoleTemplates.operator());
        SysRole viewer = role(RoleTemplates.VIEWER, "只读", "CUSTOM");
        roleMenuBinder.bindByPerms(viewer.getId(), RoleTemplates.viewer());
    }

    private SysRole role(String code, String name, String dataScope) {
        SysRole role = new SysRole();
        role.setTenantId(TenantContext.get());
        role.setCode(code);
        role.setName(name);
        role.setDataScope(dataScope);
        role.setBuiltIn(1);
        roleMapper.insert(role);
        return role;
    }

    private SysTenant require(Long id) {
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw BizException.notFound("租户不存在");
        }
        return tenant;
    }

    private SystemDtos.TenantVO toVo(SysTenant tenant) {
        SystemDtos.TenantVO vo = new SystemDtos.TenantVO();
        vo.setId(tenant.getId());
        vo.setName(tenant.getName());
        vo.setCode(tenant.getCode());
        vo.setContact(tenant.getContact());
        vo.setStatus(tenant.getStatus());
        vo.setCreatedAt(tenant.getCreatedAt());
        return vo;
    }
}
