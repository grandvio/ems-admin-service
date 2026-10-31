package com.ems.admin.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ems.admin.system.entity.SysRoleStation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRoleStationMapper extends BaseMapper<SysRoleStation> {
    @Delete("DELETE FROM sys_role_station WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_station WHERE station_id = #{stationId}")
    int deleteByStationId(@Param("stationId") Long stationId);
}
