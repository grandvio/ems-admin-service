package com.ems.admin.framework.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.ems.admin.framework.tenant.TenantContext;
import com.ems.admin.system.service.AuthQueryService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthQueryService authQueryService;

    public JwtAuthFilter(JwtService jwtService, AuthQueryService authQueryService) {
        this.jwtService = jwtService;
        this.authQueryService = authQueryService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    authenticate(header.substring(7).trim());
                } catch (Exception ignored) {
                    SecurityContextHolder.clearContext();
                    TenantContext.clear();
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private void authenticate(String token) {
        DecodedJWT jwt = jwtService.parse(token);
        Long userId = jwt.getClaim("uid").asLong();
        Long tenantId = jwt.getClaim("tid").asLong();
        if (userId == null || tenantId == null) {
            return;
        }
        TenantContext.set(tenantId);
        Optional<LoginUser> loginUser = authQueryService.loadUser(userId, tenantId);
        if (loginUser.isEmpty()) {
            TenantContext.clear();
            return;
        }
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                loginUser.get(), null, loginUser.get().getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
