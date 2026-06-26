package com.LastBite.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsS3Config {

    @Bean
    public AwsS3Properties awsS3Properties(
            @Value("${app.aws.s3.region}") String region,
            @Value("${app.aws.s3.bucket-name}") String bucketName,
            @Value("${app.aws.s3.upload-expire-seconds}") long uploadExpireSeconds,
            @Value("${app.aws.s3.access-expire-seconds}") long accessExpireSeconds,
            @Value("${app.aws.s3.max-image-size-mb}") long maxImageSizeMb,
            @Value("${app.aws.s3.max-video-size-mb}") long maxVideoSizeMb,
            @Value("${app.aws.s3.public-base-url}") String publicBaseUrl) {
        return new AwsS3Properties(region, bucketName, uploadExpireSeconds, accessExpireSeconds,
                maxImageSizeMb, maxVideoSizeMb, publicBaseUrl);
    }

    @Bean
    public S3Client s3Client(AwsS3Properties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(AwsS3Properties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
