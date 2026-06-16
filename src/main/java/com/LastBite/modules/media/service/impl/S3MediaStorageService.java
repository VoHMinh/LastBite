package com.LastBite.modules.media.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.media.service.MediaStorageServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3MediaStorageService implements MediaStorageServicePort {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Override
    public String createPresignedPutUrl(String bucket, String key, String contentType, Duration expiresIn) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .putObjectRequest(putObjectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            log.warn("Không thể kiểm tra object S3 {}/{}: {}", bucket, key, ex.awsErrorDetails().errorMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Không thể kiểm tra object trên S3");
        }
    }

    @Override
    public String createPresignedGetUrl(String bucket, String key, Duration expiresIn) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return s3Presigner.presignGetObject(request).url().toString();
    }
}
