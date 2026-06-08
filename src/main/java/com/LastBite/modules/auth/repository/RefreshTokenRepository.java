package com.LastBite.modules.auth.repository;

import com.LastBite.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    Optional<RefreshToken> findByIdAndUser_IdAndRevokedFalse(UUID id, UUID userId);

    /** Tìm theo hash bất kể trạng thái thu hồi — dùng để phát hiện token bị dùng lại. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(rt) > 0
            FROM RefreshToken rt
            WHERE rt.id = :sessionId
              AND rt.user.id = :userId
              AND rt.revoked = false
              AND rt.expiresAt > :now
            """)
    boolean existsActiveSession(
            @Param("sessionId") UUID sessionId,
            @Param("userId") UUID userId,
            @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true OR rt.expiresAt < :cutoff")
    int deleteExpiredOrRevoked(@Param("cutoff") Instant cutoff);
}
