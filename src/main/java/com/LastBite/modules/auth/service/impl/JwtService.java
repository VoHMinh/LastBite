package com.LastBite.modules.auth.service.impl;

import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.service.JwtServicePort;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Xử lý việc tạo JWT access token và refresh token.
 * <p>
 * Access token = JWT đã ký, không trạng thái, thời hạn ngắn.
 * Refresh token = chuỗi ngẫu nhiên opaque, được hash rồi lưu trong DB + Redis.
 */
@Slf4j
@Service
public class JwtService implements JwtServicePort {

    @Value("${jwt.signer-key}")
    private String signerKeyBase64;

    @Value("${jwt.access-token-duration:1800}")
    private long accessTokenDuration;

    @Value("${jwt.refresh-token-duration:604800}")
    private long refreshTokenDuration;

    private MACSigner macSigner;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    void init() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(signerKeyBase64);
            this.macSigner = new MACSigner(keyBytes);
            log.info("JwtService đã khởi tạo (HS512, access={}s, refresh={}s)",
                    accessTokenDuration, refreshTokenDuration);
        } catch (KeyLengthException e) {
            throw new IllegalStateException("Khóa ký JWT quá ngắn cho HS512", e);
        }
    }

    /**
     * Tạo JWT access token đã ký.
     */
    public String generateAccessToken(User user) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(accessTokenDuration);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getEmail())
                    .claim("user_id", user.getId().toString())
                    .claim("roles", List.of(user.getRole().name()))
                    .jwtID(UUID.randomUUID().toString())
                    .issuer("lastbite")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS512), claims);
            signedJWT.sign(macSigner);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Không thể tạo access token", e);
        }
    }

    /**
     * Tạo refresh token ngẫu nhiên an toàn (opaque, không phải JWT).
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash SHA-256 của refresh token để lưu trữ an toàn.
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }

    public long getAccessTokenDuration() {
        return accessTokenDuration;
    }

    public long getRefreshTokenDuration() {
        return refreshTokenDuration;
    }
}
