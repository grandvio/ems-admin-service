package com.ems.admin.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class SystemDtos {
    private SystemDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "不能为空") @Size(max = 32) String username,
            @NotBlank(message = "不能为空") @Size(min = 6, max = 64) String password,
            @NotBlank(message = "不能为空") @Pattern(regexp = "web|app", message = "只能是 web 或 app") String client
    ) {
    }

    public record LoginResponse(String token, long expiresIn, String client) {
    }

    public record PasswordRequest(
            @NotBlank(message = "不能为空") String oldPassword,
            @NotBlank(message = "不能为空") @Size(min = 6, max = 64) String newPassword
    ) {
    }

    public record TenantCreateRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,32}", message = "只能包含字母数字下划线和中划线") String code,
            @Size(max = 64) String contact,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{4,32}", message = "只能包含字母数字下划线") String adminUsername,
            @NotBlank @Size(min = 6, max = 64) String adminPassword,
            @Size(max = 64) String adminNickname
    ) {
    }

    public record TenantUpdateRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 64) String contact,
            @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status
    ) {
    }

    @Data
    public static class TenantVO {
        private Long id;
        private String name;
        private String code;
        private String contact;
        private String status;
        private LocalDateTime createdAt;
    }

    public record UserCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{4,32}", message = "只能包含字母数字下划线") String username,
            @NotBlank @Size(min = 6, max = 64) String password,
            @Size(max = 64) String nickname,
            @Size(max = 20) String phone,
            @Pattern(regexp = "ENABLED|DISABLED") String status,
            List<Long> roleIds
    ) {
    }

    public record UserUpdateRequest(
            @Size(max = 64) String nickname,
            @Size(max = 20) String phone,
            @NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status,
            List<Long> roleIds
    ) {
    }

    @Data
    public static class UserVO {
        private Long id;
        private Long tenantId;
        private String username;
        private String nickname;
        private String phone;
        private String status;
        private LocalDateTime createdAt;
        private List<Long> roleIds;
    }

    public record RoleCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}", message = "需为大写字母开头的角色编码") String code,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Pattern(regexp = "ALL|CUSTOM") String dataScope,
            @Size(max = 255) String remark
    ) {
    }

    public record RoleUpdateRequest(
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Pattern(regexp = "ALL|CUSTOM") String dataScope,
            @Size(max = 255) String remark
    ) {
    }

    public record MenuIdsRequest(@NotNull List<Long> menuIds) {
    }

    public record StationIdsRequest(@NotNull List<Long> stationIds) {
    }

    @Data
    public static class RoleVO {
        private Long id;
        private String code;
        private String name;
        private String dataScope;
        private Integer builtIn;
        private String remark;
        private List<Long> menuIds;
        private List<Long> stationIds;
    }

    public record MenuRequest(
            @NotNull Long parentId,
            @NotBlank @Size(max = 64) String name,
            @NotBlank @Pattern(regexp = "M|C|F") String menuType,
            @Size(max = 128) String path,
            @Size(max = 64) String perms,
            @NotNull Integer sortNum,
            Integer visible
    ) {
    }

    @Data
    public static class StationBrief {
        private Long id;
        private String name;
        private String code;
    }

    @Data
    public static class UserInfo {
        private Long userId;
        private Long tenantId;
        private String username;
        private String nickname;
        private String tenantName;
        private List<String> roles;
        private List<String> permissions;
        private List<MenuTreeServiceNode> menus;
        private List<StationBrief> stations;
    }

    @Data
    public static class MenuTreeServiceNode {
        private Long id;
        private Long parentId;
        private String name;
        private String menuType;
        private String path;
        private String perms;
        private Integer sortNum;
        private List<MenuTreeServiceNode> children;
    }
}
