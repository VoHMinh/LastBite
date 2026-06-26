package com.LastBite.modules.media.service;

import com.LastBite.common.config.AwsS3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MediaUrlService {

    private final MediaStorageServicePort storageService;
    private final AwsS3Properties s3Properties;

    public String signedUrlForKey(String key) {
        String normalizedKey = normalizeKey(key);
        if (normalizedKey == null) {
            return null;
        }
        return storageService.createPresignedGetUrl(
                s3Properties.bucketName(),
                normalizedKey,
                Duration.ofSeconds(Math.max(60, s3Properties.accessExpireSeconds())));
    }

    public String resolveUrl(String key, String fallbackUrlOrKey) {
        String signedFromKey = signedUrlForKey(key);
        return signedFromKey != null ? signedFromKey : resolveUrl(fallbackUrlOrKey);
    }

    public String resolveUrl(String urlOrKey) {
        String key = objectKeyFrom(urlOrKey);
        return key == null ? trimToNull(urlOrKey) : signedUrlForKey(key);
    }

    public List<String> resolveUrls(Collection<String> urlsOrKeys) {
        if (urlsOrKeys == null || urlsOrKeys.isEmpty()) {
            return List.of();
        }
        return urlsOrKeys.stream()
                .map(this::resolveUrl)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String objectKeyFrom(String urlOrKey) {
        String value = trimToNull(urlOrKey);
        if (value == null) {
            return null;
        }
        String directKey = normalizeKey(value);
        if (directKey != null) {
            return directKey;
        }
        String publicBaseUrl = s3Properties.publicBaseUrl().replaceAll("/+$", "");
        if (value.startsWith(publicBaseUrl + "/")) {
            return stripQuery(value.substring(publicBaseUrl.length() + 1));
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (host == null || path == null || path.length() <= 1) {
                return null;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String bucket = s3Properties.bucketName().toLowerCase(Locale.ROOT);
            if (normalizedHost.equals(bucket + ".s3.amazonaws.com")
                    || (normalizedHost.startsWith(bucket + ".s3.")
                    && normalizedHost.endsWith(".amazonaws.com"))) {
                return path.substring(1);
            }
            if ((normalizedHost.equals("s3.amazonaws.com")
                    || (normalizedHost.startsWith("s3.") && normalizedHost.endsWith(".amazonaws.com")))
                    && path.startsWith("/" + s3Properties.bucketName() + "/")) {
                return path.substring(s3Properties.bucketName().length() + 2);
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private String normalizeKey(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.contains("://")) {
            return null;
        }
        String withoutQuery = stripQuery(trimmed);
        return withoutQuery.startsWith("public/") || withoutQuery.startsWith("private/")
                ? withoutQuery
                : null;
    }

    private String stripQuery(String value) {
        int queryIndex = value.indexOf('?');
        return queryIndex >= 0 ? value.substring(0, queryIndex) : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
