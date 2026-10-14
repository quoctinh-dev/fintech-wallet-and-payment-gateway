package com.fintech.wallet.modules.account.dto.response;

import com.fintech.wallet.modules.account.enums.AccountStatus;
import com.fintech.wallet.modules.account.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private UUID id;
    private UUID userId;
    private AccountType accountType;
    private AccountStatus status;
    private String currency;
    private BigDecimal balance; // Số dư tính thời gian thực từ Ledger
    private OffsetDateTime createdAt;
}
