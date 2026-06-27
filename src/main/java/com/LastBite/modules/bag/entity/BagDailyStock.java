package com.LastBite.modules.bag.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.bag.enums.DailyStockSource;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.store.entity.Store;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "bag_daily_stocks", indexes = {
        @Index(name = "idx_bag_daily_stocks_bag_date", columnList = "bag_id,date", unique = true),
        @Index(name = "idx_bag_daily_stocks_store_date_status", columnList = "store_id,date,status"),
        @Index(name = "idx_bag_daily_stocks_date", columnList = "date"),
        @Index(name = "idx_bag_daily_stocks_store_date", columnList = "store_id,date")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BagDailyStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bag_id", nullable = false)
    private SurpriseBag bag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    @Builder.Default
    private int quantity = 0;

    @Column(nullable = false)
    @Builder.Default
    private int reserved = 0;

    @Column(nullable = false)
    @Builder.Default
    private int sold = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DailyStockStatus status = DailyStockStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_source", nullable = false, length = 30)
    @Builder.Default
    private DailyStockSource source = DailyStockSource.MANUAL;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    public int available() {
        return quantity - reserved - sold;
    }
}
