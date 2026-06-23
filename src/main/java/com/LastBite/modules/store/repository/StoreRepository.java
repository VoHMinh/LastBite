package com.LastBite.modules.store.repository;

import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    /** Tìm theo chủ cửa hàng — kèm lịch mở cửa để tránh N+1. */
    @EntityGraph(value = "Store.withSchedules")
    Optional<Store> findFirstByBusinessProfileOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    @EntityGraph(value = "Store.withSchedules")
    List<Store> findAllByBusinessProfileOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    @EntityGraph(value = "Store.withSchedules")
    Optional<Store> findByIdAndBusinessProfileOwnerId(UUID id, UUID ownerId);

    /** Tìm theo slug — kèm lịch mở cửa để tránh N+1. */
    @EntityGraph(value = "Store.withSchedules")
    Optional<Store> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByBusinessProfileOwnerId(UUID ownerId);

    boolean existsByIdAndBusinessProfileOwnerId(UUID id, UUID ownerId);

    @EntityGraph(value = "Store.withSchedules")
    @Query("SELECT s FROM Store s WHERE s.id = :id")
    Optional<Store> findDetailById(@Param("id") UUID id);

    @EntityGraph(value = "Store.withSchedules")
    Page<Store> findAllByVerificationStatus(VerificationStatus verificationStatus, Pageable pageable);

    List<Store> findAllByStatusAndVerificationStatus(StoreStatus status, VerificationStatus verificationStatus);

    /** Tìm cửa hàng: chỉ VERIFIED + ACTIVE, có bộ lọc tùy chọn. */
    @Query("""
        SELECT s FROM Store s
        WHERE s.verificationStatus = :verification
          AND s.status = com.LastBite.modules.store.enums.StoreStatus.ACTIVE
          AND (:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:category IS NULL OR s.category = :category)
          AND (:city IS NULL OR LOWER(s.city) = LOWER(:city))
          AND (:district IS NULL OR LOWER(s.district) = LOWER(:district))
        ORDER BY s.avgRating DESC, s.totalRatings DESC
    """)
    Page<Store> searchStores(VerificationStatus verification,
                             String keyword,
                             StoreCategory category,
                             String city,
                             String district,
                             Pageable pageable);
}
