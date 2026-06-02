package com.LastBite.modules.media.repository;

import com.LastBite.modules.media.entity.MediaUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {

    Optional<MediaUpload> findByIdAndOwnerId(UUID id, UUID ownerId);
}
