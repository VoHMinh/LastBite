package com.LastBite.common.security;

import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.time.Instant;

/**
 * Cấu hình bảo mật trung tâm — <b>một filter chain duy nhất</b>.
 * <p>
 * SecurityConfig dùng ObjectMapper bean chung để response lỗi 401/403 giữ đúng
 * contract ApiResponse của toàn hệ thống.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthConverter jwtAuthConverter;
    private final ObjectMapper objectMapper;

    /** Endpoint công khai KHÔNG yêu cầu xác thực. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/store-login",
            "/api/v1/auth/register",
            "/api/v1/auth/register-merchant",
            "/api/v1/auth/register-partner",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email-link",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/resend-verification-link",
            "/api/v1/auth/google",
            "/api/v1/auth/refresh",
            "/api/v1/payments/payos/webhook",
            "/api/v1/auth/forgot-password/**",
            "/api/v1/auth/reset-password/**",
            "/error",
    };

    /** Endpoint Swagger / OpenAPI. */
    private static final String[] SWAGGER_ENDPOINTS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // CORS — dùng bean CorsConfig
                .cors(Customizer.withDefaults())

                // Tắt CSRF vì dùng JWT stateless
                .csrf(AbstractHttpConfigurer::disable)

                // Session không trạng thái
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Quy tắc phân quyền
                .authorizeHttpRequests(auth -> auth
                        // Swagger UI — luôn công khai
                        .requestMatchers(SWAGGER_ENDPOINTS).permitAll()

                        // Endpoint auth — luôn công khai
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                        // Xem cửa hàng công khai (chỉ GET)
                        .requestMatchers(HttpMethod.GET, "/api/v1/stores/**").permitAll()

                        // Home discovery công khai (chỉ GET)
                        .requestMatchers(HttpMethod.GET, "/api/v1/home/discovery").permitAll()

                        // Discovery túi công khai (chỉ GET)
                        .requestMatchers(HttpMethod.GET, "/api/v1/bags/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/analytics/engagement-events").permitAll()

                        // Kiểm tra health
                        .requestMatchers("/actuator/health").permitAll()

                        // Preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Tất cả endpoint còn lại cần xác thực
                        .anyRequest().authenticated()
                )

                // Response JSON tùy chỉnh cho 401 / 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler())
                )

                // JWT resource server
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .jwt(jwt -> jwt
                                .decoder(jwtTokenProvider)
                                .jwtAuthenticationConverter(jwtAuthConverter)));

        return http.build();
    }

    // ── Error handlers ──

    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (req, res, authEx) -> {
            res.setStatus(HttpStatus.UNAUTHORIZED.value());
            res.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(res.getOutputStream(),
                    ApiResponse.builder()
                            .code(ErrorCode.UNAUTHENTICATED.getCode())
                            .message(ErrorCode.UNAUTHENTICATED.getDefaultMessage())
                            .path(req.getRequestURI())
                            .timestamp(Instant.now())
                            .build());
        };
    }

    private AccessDeniedHandler forbiddenHandler() {
        return (req, res, denied) -> {
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(res.getOutputStream(),
                    ApiResponse.builder()
                            .code(ErrorCode.FORBIDDEN.getCode())
                            .message(ErrorCode.FORBIDDEN.getDefaultMessage())
                            .path(req.getRequestURI())
                            .timestamp(Instant.now())
                            .build());
        };
    }
}
