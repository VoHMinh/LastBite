package com.LastBite.modules.discovery.repository;

import com.LastBite.modules.discovery.entity.PlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, String> {
}
