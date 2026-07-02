package com.LastBite.modules.user.entity;

import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.bag.entity.SurpriseBag;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favorite_bags", uniqueConstraints = {
        @UniqueConstraint(name = "uq_favorite_bags_user_bag", columnNames = {"user_id", "bag_id"})
}, indexes = {
        @Index(name = "idx_favorite_bags_user_id", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteBag {

    @EmbeddedId
    private FavoriteBagId id = new FavoriteBagId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @MapsId("bagId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id", nullable = false, updatable = false)
    private SurpriseBag bag;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class FavoriteBagId implements java.io.Serializable {
        @Column(name = "user_id", nullable = false, updatable = false)
        private UUID userId;

        @Column(name = "bag_id", nullable = false, updatable = false)
        private UUID bagId;
    }
}
