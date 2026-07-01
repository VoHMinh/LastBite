package com.LastBite.modules.review.repository;

import com.LastBite.modules.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByOrderId(UUID orderId);
    Optional<Review> findByOrderId(UUID orderId);
    List<Review> findByStoreIdAndVisibleTrueOrderByCreatedAtDesc(UUID storeId);
    List<Review> findByBagIdAndVisibleTrueOrderByCreatedAtDesc(UUID bagId);

    @Query("""
        SELECT COUNT(r), AVG(r.overallRating), AVG(r.collectionRating), AVG(r.qualityRating),
               AVG(r.varietyRating), AVG(r.quantityRating),
               SUM(CASE WHEN r.createdAt >= :recentAfter THEN 1 ELSE 0 END)
        FROM Review r
        WHERE r.store.id = :storeId AND r.visible = true
    """)
    Object aggregateVisibleRatings(@Param("storeId") UUID storeId, @Param("recentAfter") Instant recentAfter);
}
