package com.LastBite.modules.analytics.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.analytics.enums.EngagementEventType;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(name = "store_engagement_events", indexes = {
        @Index(name = "idx_store_engagement_store_time", columnList = "store_id,occurred_at"),
        @Index(name = "idx_store_engagement_bag_time", columnList = "bag_id,occurred_at"),
        @Index(name = "idx_store_engagement_type_time", columnList = "event_type,occurred_at"),
        @Index(name = "idx_store_engagement_source", columnList = "source")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StoreEngagementEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id")
    private SurpriseBag bag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EngagementEventType eventType;

    @Column(name = "source", length = 80)
    private String source;

    @Column(name = "session_id", length = 120)
    private String sessionId;

    @Column(name = "referrer", length = 500)
    private String referrer;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
