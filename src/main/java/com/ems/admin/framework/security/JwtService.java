package com.ems.admin.framework.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ems.admin.framework.config.EmsProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final Algorithm algorithm;
    private final long expireSeconds;

    public JwtService(EmsProperties properties) {
        this.algorithm = Algorithm.HMAC256(properties.getJwt().getSecret());
        this.expireSeconds = properties.getJwt().getExpireMinutes() * 60;
    }

    public String sign(Long userId, Long tenantId, String client) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer("ems-admin")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expireSeconds)))
                .withClaim("uid", userId)
                .withClaim("tid", tenantId)
                .withClaim("client", client)
                .sign(algorithm);
    }

    public DecodedJWT parse(String token) {
        return JWT.require(algorithm).withIssuer("ems-admin").build().verify(token);
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
