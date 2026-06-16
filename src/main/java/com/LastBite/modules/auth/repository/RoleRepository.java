package com.LastBite.modules.auth.repository;

import com.LastBite.modules.auth.entity.Role;
import com.LastBite.modules.auth.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(UserRole code);
}
