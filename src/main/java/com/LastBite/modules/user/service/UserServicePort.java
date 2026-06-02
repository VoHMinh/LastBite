package com.LastBite.modules.user.service;

import com.LastBite.modules.auth.dto.response.UserResponse;
import com.LastBite.modules.user.dto.request.ChangePasswordRequest;
import com.LastBite.modules.user.dto.request.UpdateProfileRequest;

import java.util.UUID;

public interface UserServicePort {

    UserResponse getProfile(UUID userId);

    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);

    void changePassword(UUID userId, ChangePasswordRequest request);
}
