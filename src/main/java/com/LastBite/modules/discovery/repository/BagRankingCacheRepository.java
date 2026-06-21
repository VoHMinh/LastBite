package com.LastBite.modules.discovery.repository;

import com.LastBite.modules.discovery.entity.BagRankingCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BagRankingCacheRepository extends JpaRepository<BagRankingCache, UUID> {
}
