package com.LastBite.modules.store.entity;

import com.LastBite.common.entity.BaseEntity;
import com.LastBite.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "store_special_hours",
        uniqueConstraints = @UniqueConstraint(name = "uq_store_special_hours",
                columnNames = {"store_id", "special_date"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StoreSpecialHours extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "special_date", nullable = false)
    private LocalDate specialDate;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(name = "is_closed", nullable = false)
    @Builder.Default
    private boolean closed = false;

    @Column(length = 500)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;
}
