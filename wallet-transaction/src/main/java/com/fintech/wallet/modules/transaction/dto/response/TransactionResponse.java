package com.fintech.wallet.modules.transaction.dto.response;

import com.fintech.wallet.modules.transaction.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID id;
    private UUID accountId;
    private BigDecimal amount;
    private String idempotencyKey;
    private String description;
    private TransactionStatus status;
    private OffsetDateTime createdAt;
}
