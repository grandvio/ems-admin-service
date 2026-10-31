package com.ems.admin.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.ems.admin.asset.entity.EmsStation;
import com.ems.admin.asset.mapper.EmsStationMapper;
import com.ems.admin.common.exception.BizException;
import com.ems.admin.framework.security.CurrentUser;
import com.ems.admin.framework.security.LoginUser;
import com.ems.admin.system.entity.SysRole;
import com.ems.admin.system.entity.SysRoleStation;
import com.ems.admin.system.entity.SysUserRole;
import com.ems.admin.system.mapper.SysRoleMapper;
import com.ems.admin.system.mapper.SysRoleStationMapper;
import com.ems.admin.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StationGuard {
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleStationMapper roleStationMapper;
    private final EmsStationMapper stationMapper;

    public StationGuard(SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper,
                        SysRoleStationMapper roleStationMapper, EmsStationMapper stationMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.roleStationMapper = roleStationMapper;
        this.stationMapper = stationMapper;
    }

    /**
     * @return null 表示本租户全部电站；空列表表示没有任何电站
     */
    public List<Long> visibleStationIds() {
        LoginUser user = CurrentUser.require();
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getUserId()))
                .stream().map(SysUserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectByIds(roleIds);
        boolean all = roles.stream().anyMatch(role -> "ALL".equals(role.getDataScope()));
        if (all) {
            return null;
        }
        return roleStationMapper.selectList(new LambdaQueryWrapper<SysRoleStation>().in(SysRoleStation::getRoleId, roleIds))
                .stream().map(SysRoleStation::getStationId).distinct().toList();
    }

    public void check(Long stationId) {
        if (stationId == null) {
            throw BizException.badRequest("电站不能为空");
        }
        EmsStation station = stationMapper.selectById(stationId);
        if (station == null) {
            throw BizException.notFound("电站不存在");
        }
        List<Long> visible = visibleStationIds();
        if (visible != null && !visible.contains(stationId)) {
            throw BizException.forbidden("无权访问该电站");
        }
    }

    public <T> void apply(LambdaQueryWrapper<T> wrapper, SFunction<T, Long> stationColumn) {
        List<Long> visible = visibleStationIds();
        if (visible == null) {
            return;
        }
        if (visible.isEmpty()) {
            wrapper.eq(stationColumn, -1L);
            return;
        }
        wrapper.in(stationColumn, visible);
    }
}
