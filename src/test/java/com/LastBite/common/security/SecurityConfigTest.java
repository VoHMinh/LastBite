package com.LastBite.common.security;

import com.LastBite.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void constructorFallsBackToConfiguredMapperWhenSpringMapperBeanIsMissing() throws Exception {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(JwtTokenProvider.class),
                new JwtAuthConverter(),
                missingObjectMapperProvider());

        ObjectMapper mapper = extractObjectMapper(securityConfig);
        String json = mapper.writeValueAsString(ApiResponse.builder()
                .code(4010)
                .message("Unauthorized")
                .path("/api/v1/private")
                .timestamp(Instant.parse("2026-06-30T00:00:00Z"))
                .build());

        assertTrue(json.contains("\"timestamp\":\"2026-06-30T00:00:00Z\""));
    }

    private static ObjectProvider<ObjectMapper> missingObjectMapperProvider() {
        return new ObjectProvider<>() {
            @Override
            public ObjectMapper getIfAvailable(Supplier<ObjectMapper> defaultSupplier) {
                return defaultSupplier.get();
            }
        };
    }

    private static ObjectMapper extractObjectMapper(SecurityConfig securityConfig) throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        return (ObjectMapper) field.get(securityConfig);
    }
}
