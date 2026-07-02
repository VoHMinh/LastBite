package com.LastBite.modules.refund.repository;

import com.LastBite.modules.refund.entity.RefundRequest;
import com.LastBite.modules.refund.enums.RefundReason;
import com.LastBite.modules.refund.enums.RefundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {
    List<RefundRequest> findByOrderId(UUID orderId);
    Optional<RefundRequest> findFirstByOrderId(UUID orderId);
    boolean existsByOrderId(UUID orderId);

    long countByRequestedBy_IdAndStatusIn(UUID userId, Collection<RefundStatus> statuses);

    @Query("""
        SELECT COUNT(r) FROM RefundRequest r
        WHERE r.order.store.businessProfile.owner.id = :ownerId
          AND r.status IN :statuses
    """)
    long countByOwnerIdAndStatusIn(@Param("ownerId") UUID ownerId,
                                   @Param("statuses") Collection<RefundStatus> statuses);

    @EntityGraph(attributePaths = {"order", "order.user", "payment", "requestedBy", "reviewedBy"})
    @Query("""
        SELECT r FROM RefundRequest r
        WHERE (:status IS NULL OR r.status = :status)
          AND (:reason IS NULL OR r.reason = :reason)
    """)
    Page<RefundRequest> searchAdmin(@Param("status") RefundStatus status,
                                    @Param("reason") RefundReason reason,
                                    Pageable pageable);
    @Query("""
        SELECT COUNT(r) FROM RefundRequest r
        WHERE r.order.store.id = :storeId
          AND r.reason IN :reasons
          AND r.status IN :statuses
          AND COALESCE(r.reviewedAt, r.createdAt) >= :from
    """)
    long countStoreFaultRefundsSince(@Param("storeId") UUID storeId,
                                     @Param("reasons") Collection<RefundReason> reasons,
                                     @Param("statuses") Collection<RefundStatus> statuses,
                                     @Param("from") Instant from);
}
