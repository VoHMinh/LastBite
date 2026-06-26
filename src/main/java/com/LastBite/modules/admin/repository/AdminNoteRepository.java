package com.LastBite.modules.admin.repository;

import com.LastBite.modules.admin.entity.AdminNote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminNoteRepository extends JpaRepository<AdminNote, UUID> {
    @EntityGraph(attributePaths = "actor")
    List<AdminNote> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, UUID targetId);
}
