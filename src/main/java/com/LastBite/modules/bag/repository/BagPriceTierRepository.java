package com.LastBite.modules.bag.repository;

import com.LastBite.modules.bag.entity.BagPriceTier;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.store.enums.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BagPriceTierRepository extends JpaRepository<BagPriceTier, UUID> {

    Optional<BagPriceTier> findByCategoryAndBagSizeAndActiveTrue(StoreCategory category, BagSize bagSize);

    Optional<BagPriceTier> findByCategoryAndBagSize(StoreCategory category, BagSize bagSize);

    boolean existsByCategoryAndBagSize(StoreCategory category, BagSize bagSize);

    List<BagPriceTier> findByActiveTrueOrderByCategoryAscBagSizeAsc();

    List<BagPriceTier> findByCategoryAndActiveTrueOrderByBagSizeAsc(StoreCategory category);
}
