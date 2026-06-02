package com.LastBite.modules.auth.service;

import com.LastBite.modules.auth.dto.response.AuthResponse;

public interface GoogleAuthServicePort {

    AuthResponse authenticateWithGoogle(String idTokenString);
}
