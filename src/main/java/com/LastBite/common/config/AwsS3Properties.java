package com.LastBite.common.config;

public record AwsS3Properties(
        String region,
        String bucketName,
        long uploadExpireSeconds,
        long maxImageSizeMb,
        long maxVideoSizeMb,
        String publicBaseUrl
) {

    public long maxImageSizeBytes() {
        return maxImageSizeMb * 1024 * 1024;
    }

    public long maxVideoSizeBytes() {
        return maxVideoSizeMb * 1024 * 1024;
    }

    public String publicUrl(String key) {
        return publicBaseUrl.replaceAll("/+$", "") + "/" + key;
    }
}
