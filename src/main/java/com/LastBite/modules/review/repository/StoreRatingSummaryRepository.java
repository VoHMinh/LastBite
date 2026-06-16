package com.LastBite.modules.review.repository;

import com.LastBite.modules.review.entity.StoreRatingSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoreRatingSummaryRepository extends JpaRepository<StoreRatingSummary, UUID> {
}
