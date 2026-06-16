package com.LastBite.modules.auth.service;

import com.LastBite.modules.auth.dto.request.LoginRequest;
import com.LastBite.modules.auth.dto.request.StoreLoginRequest;
import com.LastBite.modules.auth.dto.request.InitialPasswordChangeRequest;
import com.LastBite.modules.auth.dto.request.RegisterPartnerRequest;
import com.LastBite.modules.auth.dto.request.RegisterRequest;
import com.LastBite.modules.auth.dto.request.ResendOtpRequest;
import com.LastBite.modules.auth.dto.request.VerifyEmailRequest;
import com.LastBite.modules.auth.dto.response.AuthResponse;
import com.LastBite.modules.auth.dto.response.UserResponse;

import java.util.UUID;

public interface AuthServicePort {

    void register(RegisterRequest request);

    void registerMerchant(RegisterRequest request);

    void registerPartner(RegisterPartnerRequest request);

    AuthResponse verifyEmail(VerifyEmailRequest request);

    AuthResponse verifyEmailLink(String rawToken);

    void resendOtp(ResendOtpRequest request);

    void resendVerificationLink(ResendOtpRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse storeLogin(StoreLoginRequest request);

    void changeInitialPassword(UUID userId, InitialPasswordChangeRequest request);

    AuthResponse refresh(String rawRefreshToken);

    void logout(UUID userId, UUID sessionId);

    void logoutAll(UUID userId);

    UserResponse getCurrentUser(UUID userId);
}
