package com.LastBite.modules.auth.service;

import com.LastBite.modules.auth.entity.User;

import java.util.List;
import java.util.UUID;

public interface JwtServicePort {

    String generateAccessToken(User user, UUID sessionId);

    String generateAccessToken(User user, UUID sessionId, List<String> roles, UUID storeId);

    String generateRefreshToken();

    String hashToken(String rawToken);

    long getAccessTokenDuration();

    long getRefreshTokenDuration();
}
