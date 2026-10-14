package com.fintech.wallet.modules.account.dto.response;

import com.fintech.wallet.modules.account.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountSearchResponse {
    private UUID accountId;
    private UUID userId;
    private String fullName;
    private String phoneNumber;
    private String currency;
    private AccountStatus status;
}
