package com.LastBite.modules.notification.repository;

import com.LastBite.modules.notification.entity.NotificationDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeviceRepository extends JpaRepository<NotificationDevice, UUID> {

    Optional<NotificationDevice> findByUserIdAndDeviceToken(UUID userId, String deviceToken);

    List<NotificationDevice> findByUserIdAndActiveTrue(UUID userId);

    @Query("SELECT d FROM NotificationDevice d JOIN FETCH d.user WHERE d.user.id IN :userIds AND d.active = true")
    List<NotificationDevice> findByUserIdInAndActiveTrue(@Param("userIds") List<UUID> userIds);

    @Query("SELECT d FROM NotificationDevice d JOIN FETCH d.user WHERE d.active = true")
    List<NotificationDevice> findAllActive();

    @Modifying
    @Query("UPDATE NotificationDevice d SET d.active = false WHERE d.user.id = :userId AND d.active = true")
    int deactivateAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE NotificationDevice d SET d.active = false WHERE d.deviceToken = :token AND d.user.id <> :excludeUserId AND d.active = true")
    int deactivateTokenForOtherUsers(@Param("token") String token, @Param("excludeUserId") UUID excludeUserId);

    @Modifying
    @Query("UPDATE NotificationDevice d SET d.active = false WHERE d.deviceToken = :token AND d.active = true")
    int deactivateByToken(@Param("token") String token);

    long countByActiveTrue();
}
