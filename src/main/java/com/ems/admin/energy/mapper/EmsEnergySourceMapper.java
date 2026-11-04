package com.ems.admin.energy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ems.admin.energy.entity.EmsEnergySource;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmsEnergySourceMapper extends BaseMapper<EmsEnergySource> {
    /**
     * 物理删除。逻辑删除会占住设备点位唯一键，同一点位无法再次配置。
     */
    @Delete("DELETE FROM ems_energy_source WHERE id = #{id}")
    int deletePhysicalById(@Param("id") Long id);

    @Delete("DELETE FROM ems_energy_source WHERE device_id = #{deviceId}")
    int deletePhysicalByDeviceId(@Param("deviceId") Long deviceId);
}
