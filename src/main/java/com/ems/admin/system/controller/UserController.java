package com.ems.admin.system.controller;

import com.ems.admin.common.api.PageResult;
import com.ems.admin.common.api.R;
import com.ems.admin.system.Perms;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Perms.USER_LIST + "')")
    public R<PageResult<SystemDtos.UserVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize,
                                                 @RequestParam(required = false) String username,
                                                 @RequestParam(required = false) String nickname) {
        return R.ok(userService.page(pageNum, pageSize, username, nickname));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.USER_LIST + "')")
    public R<SystemDtos.UserVO> get(@PathVariable Long id) {
        return R.ok(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Perms.USER_ADD + "')")
    public R<Long> create(@Valid @RequestBody SystemDtos.UserCreateRequest request) {
        return R.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.USER_EDIT + "')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SystemDtos.UserUpdateRequest request) {
        userService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Perms.USER_REMOVE + "')")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}
