package com.LastBite.common.config;

import com.LastBite.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void objectMapperBeanSerializesApiResponseWithInstant() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.builder()
                .code(4010)
                .message("Unauthorized")
                .path("/api/v1/private")
                .timestamp(Instant.parse("2026-06-30T00:00:00Z"))
                .build());

        assertTrue(json.contains("\"code\":4010"));
        assertTrue(json.contains("\"message\":\"Unauthorized\""));
        assertTrue(json.contains("\"path\":\"/api/v1/private\""));
        assertTrue(json.contains("\"timestamp\":\"2026-06-30T00:00:00Z\""));
    }
}
