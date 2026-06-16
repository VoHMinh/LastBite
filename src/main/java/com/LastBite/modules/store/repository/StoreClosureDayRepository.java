package com.LastBite.modules.store.repository;

import com.LastBite.modules.store.entity.StoreClosureDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreClosureDayRepository extends JpaRepository<StoreClosureDay, UUID> {
    boolean existsByStoreIdAndClosedDate(UUID storeId, LocalDate closedDate);
    Optional<StoreClosureDay> findByStoreIdAndClosedDate(UUID storeId, LocalDate closedDate);
    List<StoreClosureDay> findAllByStoreIdAndClosedDateBetweenOrderByClosedDateAsc(
            UUID storeId,
            LocalDate from,
            LocalDate to);
}
