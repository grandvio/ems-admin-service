package com.ems.admin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.Pages;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.common.tx.AfterCommit;
import com.ems.admin.framework.security.CurrentUser;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.oss.ObjectStorage;
import com.ems.admin.oss.OssObjects;
import com.ems.admin.oss.OssUrls;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.entity.SysRole;
import com.ems.admin.system.entity.SysUser;
import com.ems.admin.system.entity.SysUserRole;
import com.ems.admin.system.mapper.SysRoleMapper;
import com.ems.admin.system.mapper.SysUserMapper;
import com.ems.admin.system.mapper.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final OssUrls ossUrls;
    private final ObjectStorage objectStorage;
    private final AfterCommit afterCommit;

    public UserService(SysUserMapper userMapper, SysRoleMapper roleMapper, SysUserRoleMapper userRoleMapper,
                       PasswordEncoder passwordEncoder, OssUrls ossUrls, ObjectStorage objectStorage, AfterCommit afterCommit) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.ossUrls = ossUrls;
        this.objectStorage = objectStorage;
        this.afterCommit = afterCommit;
    }

    public PageResult<SystemDtos.UserVO> page(long pageNum, long pageSize, String username, String nickname) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(SysUser::getUsername, username);
        }
        if (StringUtils.hasText(nickname)) {
            wrapper.like(SysUser::getNickname, nickname);
        }
        wrapper.orderByDesc(SysUser::getId);
        Page<SysUser> page = userMapper.selectPage(Pages.of(pageNum, pageSize), wrapper);
        List<SystemDtos.UserVO> rows = page.getRecords().stream().map(user -> toVo(user, false)).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), rows);
    }

    public SystemDtos.UserVO get(Long id) {
        return toVo(require(id), true);
    }

    @Transactional
    public Long create(SystemDtos.UserCreateRequest request) {
        assertUsernameFree(request.username());
        SysUser user = new SysUser();
        user.setTenantId(TenantContext.get());
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setPhone(request.phone());
        user.setStatus(request.status() == null ? "ENABLED" : request.status());
        userMapper.insert(user);
        replaceRoles(user.getId(), request.roleIds());
        return user.getId();
    }

    @Transactional
    public void update(Long id, SystemDtos.UserUpdateRequest request) {
        SysUser user = require(id);
        user.setNickname(request.nickname());
        user.setPhone(request.phone());
        user.setStatus(request.status());
        userMapper.updateById(user);
        if (request.roleIds() != null) {
            replaceRoles(id, request.roleIds());
        }
    }

    @Transactional
    public void delete(Long id) {
        if (id.equals(CurrentUser.require().getUserId())) {
            throw BizException.badRequest("不能删除当前登录用户");
        }
        SysUser user = require(id);
        String avatarKey = user.getAvatarKey();
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        afterCommit.run(() -> OssObjects.deleteQuietly(objectStorage, avatarKey));
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            userRoleMapper.deleteByUserId(userId);
            return;
        }
        List<SysRole> roles = roleMapper.selectByIds(roleIds);
        if (roles.size() != roleIds.stream().distinct().count()) {
            throw BizException.badRequest("角色不存在");
        }
        userRoleMapper.deleteByUserId(userId);
        for (Long roleId : roleIds.stream().distinct().toList()) {
            SysUserRole link = new SysUserRole();
            link.setTenantId(TenantContext.get());
            link.setUserId(userId);
            link.setRoleId(roleId);
            userRoleMapper.insert(link);
        }
    }

    private void assertUsernameFree(String username) {
        boolean previous = TenantContext.setIgnore(true);
        try {
            Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (count != null && count > 0) {
                throw BizException.conflict("用户名已存在");
            }
        } finally {
            TenantContext.setIgnore(previous);
        }
    }

    private SysUser require(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private SystemDtos.UserVO toVo(SysUser user, boolean withRoles) {
        SystemDtos.UserVO vo = new SystemDtos.UserVO();
        vo.setId(user.getId());
        vo.setTenantId(user.getTenantId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setAvatarUrl(ossUrls.read(user.getAvatarKey()));
        vo.setCreatedAt(user.getCreatedAt());
        if (withRoles) {
            vo.setRoleIds(userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()))
                    .stream().map(SysUserRole::getRoleId).toList());
        }
        return vo;
    }
}
