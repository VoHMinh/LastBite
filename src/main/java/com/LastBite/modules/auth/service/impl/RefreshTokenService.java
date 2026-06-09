package com.LastBite.modules.auth.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.RefreshToken;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.RefreshTokenRepository;
import com.LastBite.modules.auth.service.JwtServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Quản lý refresh token với Redis + DB và phát hiện token bị dùng lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String REDIS_PREFIX = "rt:";
    private static final String REDIS_USER_PREFIX = "rt:user:";

    private final RefreshTokenRepository refreshTokenRepository;
    private final StringRedisTemplate redisTemplate;
    private final JwtServicePort jwtService;

    @Transactional
    public IssuedRefreshToken createRefreshToken(User user) {
        String rawToken = jwtService.generateRefreshToken();
        String tokenHash = jwtService.hashToken(rawToken);

        Instant expiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenDuration());

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
        entity = refreshTokenRepository.save(entity);

        Duration ttl = Duration.between(Instant.now(), expiresAt);
        cacheToken(tokenHash, user.getId(), ttl);

        log.debug("Đã tạo refresh token cho người dùng {}", user.getEmail());
        return new IssuedRefreshToken(entity.getId(), rawToken);
    }

    /**
     * Xác thực refresh token thô và trả về ID người dùng tương ứng.
     * Redis chỉ là cache; DB vẫn được kiểm tra trạng thái thu hồi/hết hạn để
     * key Redis cũ không thể làm sống lại phiên đã bị thu hồi.
     */
    public UUID validateAndGetUserId(String rawToken) {
        String tokenHash = jwtService.hashToken(rawToken);

        RefreshToken dbToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID,
                        "Refresh token không hợp lệ"));

        ensureValidToken(dbToken);

        Duration remaining = Duration.between(Instant.now(), dbToken.getExpiresAt());
        if (!remaining.isNegative()) {
            cacheToken(tokenHash, dbToken.getUser().getId(), remaining);
        }

        return dbToken.getUser().getId();
    }

    @Transactional
    public void revokeToken(String rawToken) {
        String tokenHash = jwtService.hashToken(rawToken);
        redisTemplate.delete(REDIS_PREFIX + tokenHash);

        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    redisTemplate.opsForSet().remove(REDIS_USER_PREFIX + token.getUser().getId(), tokenHash);
                });
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        refreshTokenRepository.findByIdAndUser_IdAndRevokedFalse(sessionId, userId)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    redisTemplate.delete(REDIS_PREFIX + token.getTokenHash());
                    redisTemplate.opsForSet().remove(REDIS_USER_PREFIX + userId, token.getTokenHash());
                });
    }

    @Transactional
    public void revokeAllByUserId(UUID userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId);
        deleteCachedTokensForUser(userId);
        log.info("Đã thu hồi {} refresh token của người dùng {}", count, userId);
    }

    private void ensureValidToken(RefreshToken dbToken) {
        if (dbToken.isRevoked()) {
            UUID userId = dbToken.getUser().getId();
            log.warn("BẢO MẬT: Phát hiện refresh token bị dùng lại cho người dùng {}. Đang thu hồi toàn bộ phiên.", userId);
            revokeAllByUserId(userId);
            throw new ApiException(ErrorCode.TOKEN_REUSE_DETECTED);
        }

        if (dbToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token đã hết hạn");
        }
    }

    private void cacheToken(String tokenHash, UUID userId, Duration ttl) {
        redisTemplate.opsForValue().set(REDIS_PREFIX + tokenHash, userId.toString(), ttl);
        redisTemplate.opsForSet().add(REDIS_USER_PREFIX + userId, tokenHash);
        redisTemplate.expire(REDIS_USER_PREFIX + userId, ttl);
    }

    private void deleteCachedTokensForUser(UUID userId) {
        String userKey = REDIS_USER_PREFIX + userId;
        var tokenHashes = redisTemplate.opsForSet().members(userKey);
        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            redisTemplate.delete(tokenHashes.stream()
                    .map(hash -> REDIS_PREFIX + hash)
                    .toList());
        }
        redisTemplate.delete(userKey);
    }

    public record IssuedRefreshToken(UUID sessionId, String rawToken) {
    }
}
