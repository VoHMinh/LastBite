package com.LastBite.modules.user.repository;

import com.LastBite.modules.user.entity.FavoriteBag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FavoriteBagRepository extends JpaRepository<FavoriteBag, UUID> {

    @Query("SELECT fb FROM FavoriteBag fb JOIN FETCH fb.bag b JOIN FETCH b.store WHERE fb.user.id = :userId ORDER BY fb.createdAt DESC")
    List<FavoriteBag> findByUserIdWithBagAndStore(@Param("userId") UUID userId);

    boolean existsByUserIdAndBagId(UUID userId, UUID bagId);

    long deleteByUserIdAndBagId(UUID userId, UUID bagId);
}
