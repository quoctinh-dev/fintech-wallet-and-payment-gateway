package com.fintech.wallet.modules.transaction.dto.response;

import com.fintech.wallet.modules.transaction.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class TransferResponse {
    private UUID transactionId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private TransactionStatus status;
    private String description;
    private OffsetDateTime createdAt;
}
