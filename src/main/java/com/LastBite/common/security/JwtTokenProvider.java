package com.LastBite.common.security;

import com.LastBite.modules.auth.repository.RefreshTokenRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Bộ decode JWT tùy chỉnh dùng HMAC-SHA512.
 * <p>
 * Ủy quyền cho {@link NimbusJwtDecoder} để kiểm tra chữ ký, thời hạn và ném
 * {@code BadJwtException} phù hợp để Spring Security chuyển thành response 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider implements JwtDecoder {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.signer-key}")
    private String signerKey;

    private NimbusJwtDecoder nimbusDecoder;

    @PostConstruct
    void init() {
        byte[] keyBytes = Base64.getDecoder().decode(signerKey);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "HmacSHA512");

        this.nimbusDecoder = NimbusJwtDecoder.withSecretKey(keySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();

        log.info("JWT token provider đã khởi tạo (HS512)");
    }

    @Override
    public Jwt decode(String token) {
        Jwt jwt = nimbusDecoder.decode(token);
        ensureSessionActive(jwt);
        return jwt;
    }

    private void ensureSessionActive(Jwt jwt) {
        String userIdClaim = jwt.getClaimAsString("user_id");
        String sessionIdClaim = jwt.getClaimAsString("sid");
        if (userIdClaim == null || sessionIdClaim == null) {
            throw new BadJwtException("Token missing session data");
        }

        UUID userId;
        UUID sessionId;
        try {
            userId = UUID.fromString(userIdClaim);
            sessionId = UUID.fromString(sessionIdClaim);
        } catch (IllegalArgumentException ex) {
            throw new BadJwtException("Token has invalid session data");
        }

        if (!refreshTokenRepository.existsActiveSession(sessionId, userId, Instant.now())) {
            throw new BadJwtException("Login session is expired or revoked");
        }
    }
}
