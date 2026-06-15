package com.LastBite.modules.media.repository;

import com.LastBite.modules.media.entity.MediaUpload;
import com.LastBite.modules.media.enums.MediaPurpose;
import com.LastBite.modules.media.enums.MediaTargetType;
import com.LastBite.modules.media.enums.MediaUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaUploadRepository extends JpaRepository<MediaUpload, UUID> {

    Optional<MediaUpload> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<MediaUpload> findAllByTargetTypeAndTargetIdAndPurposeAndStatusOrderByCreatedAtAsc(
            MediaTargetType targetType,
            UUID targetId,
            MediaPurpose purpose,
            MediaUploadStatus status);

    long countByTargetTypeAndTargetIdAndPurpose(
            MediaTargetType targetType,
            UUID targetId,
            MediaPurpose purpose);
}
