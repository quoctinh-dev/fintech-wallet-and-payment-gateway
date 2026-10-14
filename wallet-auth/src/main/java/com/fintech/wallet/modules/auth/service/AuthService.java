package com.fintech.wallet.modules.auth.service;

import com.fintech.wallet.modules.auth.dto.request.LoginRequest;
import com.fintech.wallet.modules.auth.dto.request.RefreshTokenRequest;
import com.fintech.wallet.modules.auth.dto.request.RegisterRequest;
import com.fintech.wallet.modules.auth.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse refreshToken(RefreshTokenRequest request);
    void logout(String accessToken, String userId);
}