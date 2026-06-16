package com.LastBite.modules.media.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaAccessUrlResponse {
    private String accessUrl;
    private long expiresInSeconds;
}
