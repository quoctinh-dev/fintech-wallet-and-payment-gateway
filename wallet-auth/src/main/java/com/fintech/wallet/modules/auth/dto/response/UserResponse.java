package com.fintech.wallet.modules.auth.dto.response;

import com.fintech.wallet.modules.auth.enums.KycStatus;
import com.fintech.wallet.modules.auth.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String phoneNumber;
    private String fullName;
    private UserStatus status;
    private String citizenId;
    private String idCardFrontUrl;
    private String idCardBackUrl;
    private KycStatus kycStatus;
    private String kycRejectionReason;
    private String roles;
    private OffsetDateTime createdAt;
}
