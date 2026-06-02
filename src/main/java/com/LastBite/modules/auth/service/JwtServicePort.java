package com.LastBite.modules.auth.service;

import com.LastBite.modules.auth.entity.User;

public interface JwtServicePort {

    String generateAccessToken(User user);

    String generateRefreshToken();

    String hashToken(String rawToken);

    long getAccessTokenDuration();

    long getRefreshTokenDuration();
}
