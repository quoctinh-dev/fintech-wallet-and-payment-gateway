package com.fintech.wallet.modules.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String refreshToken;
    private UUID userId;
    private String fullName;
    private String kycStatus;
}