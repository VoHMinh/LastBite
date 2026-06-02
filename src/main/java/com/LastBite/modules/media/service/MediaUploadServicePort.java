package com.LastBite.modules.media.service;

import com.LastBite.modules.media.dto.request.ConfirmMediaUploadRequest;
import com.LastBite.modules.media.dto.request.CreatePresignedUploadRequest;
import com.LastBite.modules.media.dto.response.MediaUploadResponse;
import com.LastBite.modules.media.dto.response.PresignedUploadResponse;

import java.util.UUID;

public interface MediaUploadServicePort {

    PresignedUploadResponse createPresignedUploadUrl(UUID ownerId, CreatePresignedUploadRequest request);

    MediaUploadResponse confirmUpload(UUID ownerId, ConfirmMediaUploadRequest request);
}
