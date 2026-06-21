package com.LastBite.modules.discovery.entity;

import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.SurpriseBag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bag_ranking_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BagRankingCache {

    @Id
    @Column(name = "daily_stock_id", nullable = false)
    private UUID dailyStockId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_stock_id", insertable = false, updatable = false)
    private BagDailyStock dailyStock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id", nullable = false)
    private SurpriseBag bag;

    @Column(name = "rating_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal ratingScore;

    @Column(name = "discount_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal discountScore;

    @Column(name = "urgency_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal urgencyScore;

    @Column(name = "availability_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal availabilityScore;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
}
