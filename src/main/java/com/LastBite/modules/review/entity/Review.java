package com.LastBite.modules.review.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_store_visible", columnList = "store_id,visible"),
        @Index(name = "idx_reviews_user_id", columnList = "user_id")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Review extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id", nullable = false)
    private SurpriseBag bag;

    @Column(name = "overall_rating", nullable = false)
    private int overallRating;

    @Column(name = "collection_rating", nullable = false)
    private int collectionRating;

    @Column(name = "quality_rating", nullable = false)
    private int qualityRating;

    @Column(name = "variety_rating", nullable = false)
    private int varietyRating;

    @Column(name = "quantity_rating", nullable = false)
    private int quantityRating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    @Builder.Default
    private boolean visible = true;

    @Column(name = "hidden_reason", length = 1000)
    private String hiddenReason;
}
