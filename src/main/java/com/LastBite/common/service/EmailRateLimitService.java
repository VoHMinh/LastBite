package com.LastBite.common.service;

import com.LastBite.common.config.MailProperties;
import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.util.HashUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailRateLimitService {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final MailProperties mailProperties;

    public void checkAuthEmailSend(String email, String purpose) {
        MailProperties.RateLimit limits = mailProperties.getRateLimit();
        if (!limits.isEnabled()) {
            return;
        }

        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String emailHash = HashUtil.sha256(normalizedEmail);
        String safePurpose = safePurpose(purpose);
        try {
            check("mail:rate:email:" + safePurpose + ":" + emailHash + ":minute",
                    ONE_MINUTE,
                    Math.max(1, limits.getPerEmailPerMinute()),
                    "Vui long cho 1 phut truoc khi yeu cau email moi.");
            check("mail:rate:email:" + safePurpose + ":" + emailHash + ":hour",
                    ONE_HOUR,
                    Math.max(1, limits.getPerEmailPerHour()),
                    "Ban da yeu cau qua nhieu email. Vui long thu lai sau.");

            String ip = currentClientIp();
            if (ip != null && !ip.isBlank()) {
                String ipHash = HashUtil.sha256(ip);
                check("mail:rate:ip:" + safePurpose + ":" + ipHash + ":minute",
                        ONE_MINUTE,
                        Math.max(1, limits.getPerIpPerMinute()),
                        "Co qua nhieu yeu cau gui email tu dia chi hien tai. Vui long thu lai sau.");
                check("mail:rate:ip:" + safePurpose + ":" + ipHash + ":hour",
                        ONE_HOUR,
                        Math.max(1, limits.getPerIpPerHour()),
                        "Co qua nhieu yeu cau gui email tu dia chi hien tai. Vui long thu lai sau.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Email rate limit check failed-open for {}: {}", normalizedEmail, e.getMessage());
        }
    }

    private void check(String key, Duration ttl, int limit, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        if (count != null && count > limit) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, message);
        }
    }

    private String currentClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String safePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return "auth";
        }
        return purpose.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
    }
}
