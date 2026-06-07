package com.LastBite.modules.notification.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.notification.enums.DeviceType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "notification_devices", uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_devices_user_token", columnNames = {"user_id", "device_token"})
}, indexes = {
        @Index(name = "idx_notification_devices_user_active", columnList = "user_id,is_active"),
        @Index(name = "idx_notification_devices_token_active", columnList = "device_token,is_active")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDevice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_token", nullable = false, length = 500)
    private String deviceToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    private DeviceType deviceType;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
