package com.LastBite.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chuyển {@link Jwt} đã decode thành {@link JwtAuthenticationToken}
 * của Spring Security kèm danh sách quyền đã trích xuất.
 * <p>
 * Không phụ thuộc Redis/session.
 * Role được đọc trực tiếp từ claim {@code roles} trong JWT.
 * Khi cần quản lý session sâu hơn, mở rộng class này.
 * <p>
 * Các claim JWT mong đợi:
 * <ul>
 *   <li>{@code sub} — user identifier (email / phone)</li>
 *   <li>{@code user_id} — UUID of the user</li>
 *   <li>{@code roles} — list of role strings (e.g. ["CUSTOMER", "STORE_OWNER"])</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String userId = jwt.getClaimAsString("user_id");

        if (userId == null) {
            throw new JwtException("Thiếu claim JWT bắt buộc: user_id");
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        Boolean mustChangePassword = jwt.getClaim("must_change_password");
        if (Boolean.TRUE.equals(mustChangePassword)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PASSWORD_CHANGE_REQUIRED"));
            String principal = jwt.getSubject() != null ? jwt.getSubject() : userId;
            return new JwtAuthenticationToken(jwt, authorities, principal);
        }

        // Trích xuất role từ claim JWT
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }
        } else {
            // Role mặc định nếu token không khai báo role
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        // Trích xuất permission tùy chọn để phân quyền chi tiết hơn
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        if (permissions != null) {
            for (String perm : permissions) {
                authorities.add(new SimpleGrantedAuthority("PERM_" + perm));
            }
        }

        String principal = jwt.getSubject() != null ? jwt.getSubject() : userId;

        return new JwtAuthenticationToken(jwt, authorities, principal);
    }
}
