package com.LastBite.common.service;

import com.LastBite.common.config.MailProperties;
import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailRateLimitServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final MailProperties mailProperties = new MailProperties();
    private final EmailRateLimitService service = new EmailRateLimitService(redisTemplate, mailProperties);

    @Test
    void allowsRequestWithinConfiguredEmailLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        service.checkAuthEmailSend("Customer@Example.com", "otp");

        verify(redisTemplate, times(2)).expire(anyString(), any());
    }

    @Test
    void throwsTooManyRequestsWhenEmailMinuteLimitIsExceeded() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.checkAuthEmailSend("customer@example.com", "otp"));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getErrorCode());
    }

    @Test
    void skipsRedisWhenMailRateLimitDisabled() {
        mailProperties.getRateLimit().setEnabled(false);

        service.checkAuthEmailSend("customer@example.com", "otp");

        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
    }
}
