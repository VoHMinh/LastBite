package com.LastBite.modules.media.dto.response;

import com.LastBite.modules.media.enums.MediaPurpose;
import com.LastBite.modules.media.enums.MediaTargetType;
import com.LastBite.modules.media.enums.MediaType;
import com.LastBite.modules.media.enums.MediaUploadStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MediaUploadResponse {
    private UUID uploadId;
    private MediaPurpose purpose;
    private MediaType mediaType;
    private MediaTargetType targetType;
    private UUID targetId;
    private String key;
    private String publicUrl;
    private String contentType;
    private long fileSize;
    private MediaUploadStatus status;
    private Instant confirmedAt;
}
