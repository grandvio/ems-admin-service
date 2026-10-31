package com.ems.admin.system.controller;

import com.ems.admin.common.api.R;
import com.ems.admin.system.dto.SystemDtos;
import com.ems.admin.system.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public R<SystemDtos.LoginResponse> login(@Valid @RequestBody SystemDtos.LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @GetMapping("/info")
    public R<SystemDtos.UserInfo> info() {
        return R.ok(authService.info());
    }

    @PutMapping("/password")
    public R<Void> password(@Valid @RequestBody SystemDtos.PasswordRequest request) {
        authService.changePassword(request);
        return R.ok();
    }
}
