package com.LastBite.modules.media.service;

import java.time.Duration;

public interface MediaStorageServicePort {

    String createPresignedPutUrl(String bucket, String key, String contentType, Duration expiresIn);

    String createPresignedGetUrl(String bucket, String key, Duration expiresIn);

    boolean objectExists(String bucket, String key);
}
