package com.LastBite.modules.store.repository;

import com.LastBite.modules.store.entity.StoreSpecialHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreSpecialHoursRepository extends JpaRepository<StoreSpecialHours, UUID> {
    Optional<StoreSpecialHours> findByStoreIdAndSpecialDate(UUID storeId, LocalDate specialDate);
    List<StoreSpecialHours> findAllByStoreIdAndSpecialDateBetweenOrderBySpecialDateAsc(
            UUID storeId,
            LocalDate from,
            LocalDate to);
}
