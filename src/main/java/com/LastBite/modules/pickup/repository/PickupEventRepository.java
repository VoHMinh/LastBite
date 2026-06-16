package com.LastBite.modules.pickup.repository;

import com.LastBite.modules.pickup.entity.PickupEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PickupEventRepository extends JpaRepository<PickupEvent, UUID> {
}
