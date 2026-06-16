package com.LastBite.modules.merchant.repository;

import com.LastBite.modules.merchant.entity.ReviewFeedbackItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewFeedbackItemRepository extends JpaRepository<ReviewFeedbackItem, UUID> {
    List<ReviewFeedbackItem> findAllByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
