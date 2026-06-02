package com.LastBite.modules.media.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PresignedUploadResponse {
    private UUID uploadId;
    private String uploadUrl;
    private String key;
    private String publicUrl;
    private long expiresInSeconds;
}
