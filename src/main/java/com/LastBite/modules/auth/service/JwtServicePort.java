package com.LastBite.modules.auth.service;

import com.LastBite.modules.auth.entity.User;

import java.util.UUID;

public interface JwtServicePort {

    String generateAccessToken(User user, UUID sessionId);

    String generateRefreshToken();

    String hashToken(String rawToken);

    long getAccessTokenDuration();

    long getRefreshTokenDuration();
}
