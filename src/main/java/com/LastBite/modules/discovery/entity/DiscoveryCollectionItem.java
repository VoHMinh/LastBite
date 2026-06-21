package com.LastBite.modules.discovery.entity;

import com.LastBite.modules.bag.entity.SurpriseBag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discovery_collection_items", uniqueConstraints = {
        @UniqueConstraint(name = "uq_discovery_collection_items_collection_bag",
                columnNames = {"collection_id", "bag_id"})
}, indexes = {
        @Index(name = "idx_discovery_collection_items_collection", columnList = "collection_id,pinned_order")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoveryCollectionItem {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id", nullable = false)
    private DiscoveryCollection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id", nullable = false)
    private SurpriseBag bag;

    @Column(name = "pinned_order")
    private Integer pinnedOrder;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;
}
