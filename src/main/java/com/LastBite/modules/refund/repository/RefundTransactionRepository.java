package com.LastBite.modules.refund.repository;

import com.LastBite.modules.refund.entity.RefundTransaction;
import com.LastBite.modules.refund.enums.RefundTransactionMethod;
import com.LastBite.modules.refund.enums.RefundTransactionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, UUID> {

    List<RefundTransaction> findAllByRefundRequestIdOrderByCreatedAtAsc(UUID refundRequestId);

    boolean existsByRefundRequestIdAndStatus(UUID refundRequestId, RefundTransactionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t FROM RefundTransaction t
        JOIN FETCH t.refundRequest r
        JOIN FETCH r.order o
        LEFT JOIN FETCH r.payment
        WHERE t.status = :status
          AND t.method = :method
        ORDER BY t.createdAt ASC
    """)
    List<RefundTransaction> findPendingForProcessing(@Param("status") RefundTransactionStatus status,
                                                     @Param("method") RefundTransactionMethod method,
                                                     Pageable pageable);
}
