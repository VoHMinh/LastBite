package com.LastBite.modules.review.repository;

import com.LastBite.modules.review.entity.ReviewReport;
import com.LastBite.modules.review.enums.ReviewReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {
    Page<ReviewReport> findAllByStatus(ReviewReportStatus status, Pageable pageable);
}
