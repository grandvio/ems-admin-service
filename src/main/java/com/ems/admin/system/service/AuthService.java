package com.ems.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.asset.service.StationGuard;
import com.ems.admin.common.TenantIds;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.security.CurrentUser;
import com.ems.admin.oss.OssUrls;
import com.ems.admin.framework.security.JwtService;
import com.ems.admin.framework.security.LoginUser;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.entity.SysRole;
import com.ems.admin.system.entity.SysTenant;
import com.ems.admin.system.entity.SysUser;
import com.ems.admin.system.mapper.SysTenantMapper;
import com.ems.admin.system.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthQueryService authQueryService;
    private final MenuTreeService menuTreeService;
    private final StationGuard stationGuard;
    private final EmsStationMapper stationMapper;
    private final OssUrls ossUrls;

    public AuthService(SysUserMapper userMapper, SysTenantMapper tenantMapper, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthQueryService authQueryService, MenuTreeService menuTreeService,
                       StationGuard stationGuard, EmsStationMapper stationMapper, OssUrls ossUrls) {
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authQueryService = authQueryService;
        this.menuTreeService = menuTreeService;
        this.stationGuard = stationGuard;
        this.stationMapper = stationMapper;
        this.ossUrls = ossUrls;
    }

    public SystemDtos.LoginResponse login(SystemDtos.LoginRequest request) {
        boolean previous = TenantContext.setIgnore(true);
        SysUser user;
        try {
            user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.username()));
        } finally {
            TenantContext.setIgnore(previous);
        }
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (!"ENABLED".equals(user.getStatus())) {
            throw BizException.unauthorized("账号已停用");
        }
        SysTenant tenant = tenantMapper.selectById(user.getTenantId());
        if (tenant == null || !"ENABLED".equals(tenant.getStatus())) {
            throw BizException.unauthorized("租户已停用");
        }
        String token = jwtService.sign(user.getId(), user.getTenantId(), request.client());
        return new SystemDtos.LoginResponse(token, jwtService.getExpireSeconds(), request.client());
    }

    public void changePassword(SystemDtos.PasswordRequest request) {
        LoginUser loginUser = CurrentUser.require();
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null || !passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BizException.badRequest("原密码不正确");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    public SystemDtos.UserInfo info() {
        LoginUser loginUser = CurrentUser.require();
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        SysTenant tenant = tenantMapper.selectById(user.getTenantId());
        SystemDtos.UserInfo info = new SystemDtos.UserInfo();
        info.setUserId(user.getId());
        info.setTenantId(user.getTenantId());
        info.setUsername(user.getUsername());
        info.setNickname(user.getNickname());
        info.setAvatarUrl(ossUrls.read(user.getAvatarKey()));
        info.setTenantName(tenant == null ? null : tenant.getName());
        List<SysRole> roles = authQueryService.rolesOf(user.getId());
        info.setRoles(roles.stream().map(SysRole::getCode).toList());
        info.setPermissions(authQueryService.permissionsOf(user.getId()));
        info.setMenus(menuTreeService.toTree(menuTreeService.withAncestors(authQueryService.menusOf(user.getId()))).stream()
                .map(this::toNode).toList());
        info.setStations(visibleStations());
        if (TenantIds.isPlatform(user.getTenantId())) {
            info.setTenantName(tenant == null ? "平台" : tenant.getName());
        }
        return info;
    }

    private List<SystemDtos.StationBrief> visibleStations() {
        List<Long> ids = stationGuard.visibleStationIds();
        LambdaQueryWrapper<EmsStation> wrapper = new LambdaQueryWrapper<>();
        if (ids != null) {
            if (ids.isEmpty()) {
                return List.of();
            }
            wrapper.in(EmsStation::getId, ids);
        }
        wrapper.orderByAsc(EmsStation::getId);
        return stationMapper.selectList(wrapper).stream().map(station -> {
            SystemDtos.StationBrief brief = new SystemDtos.StationBrief();
            brief.setId(station.getId());
            brief.setName(station.getName());
            brief.setCode(station.getCode());
            return brief;
        }).toList();
    }

    private SystemDtos.MenuTreeServiceNode toNode(MenuTreeService.MenuNode source) {
        SystemDtos.MenuTreeServiceNode node = new SystemDtos.MenuTreeServiceNode();
        node.setId(source.getId());
        node.setParentId(source.getParentId());
        node.setName(source.getName());
        node.setMenuType(source.getMenuType());
        node.setPath(source.getPath());
        node.setPerms(source.getPerms());
        node.setSortNum(source.getSortNum());
        node.setChildren(source.getChildren().stream().map(this::toNode).toList());
        return node;
    }
}
