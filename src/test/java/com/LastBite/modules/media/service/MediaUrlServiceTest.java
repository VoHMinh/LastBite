package com.LastBite.modules.media.service;

import com.LastBite.common.config.AwsS3Properties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaUrlServiceTest {

    private final MediaStorageServicePort storageService = mock(MediaStorageServicePort.class);
    private final AwsS3Properties properties = new AwsS3Properties(
            "ap-southeast-1", "lastbite", 300, 3600, 5, 50,
            "https://lastbite.s3.amazonaws.com");
    private final MediaUrlService service = new MediaUrlService(storageService, properties);

    @Test
    void signsStoredS3PublicUrl() {
        String key = "public/store/store-id/image.jpg";
        when(storageService.createPresignedGetUrl(
                eq("lastbite"), eq(key), eq(Duration.ofSeconds(3600))))
                .thenReturn("https://signed.example/image.jpg");

        String result = service.resolveUrl("https://lastbite.s3.amazonaws.com/" + key);

        assertEquals("https://signed.example/image.jpg", result);
    }

    @Test
    void keepsExternalUrlUnchanged() {
        String url = "https://lh3.googleusercontent.com/avatar.jpg";

        String result = service.resolveUrl(url);

        assertEquals(url, result);
    }
}
