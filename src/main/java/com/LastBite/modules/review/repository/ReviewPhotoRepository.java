package com.LastBite.modules.review.repository;

import com.LastBite.modules.review.entity.ReviewPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewPhotoRepository extends JpaRepository<ReviewPhoto, UUID> {
    List<ReviewPhoto> findAllByReviewId(UUID reviewId);
}
