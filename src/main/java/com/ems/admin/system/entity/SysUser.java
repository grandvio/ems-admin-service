package com.ems.admin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ems.admin.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    @ToString.Exclude
    private String password;
    private String nickname;
    private String phone;
    private String status;
    private String avatarKey;
}
