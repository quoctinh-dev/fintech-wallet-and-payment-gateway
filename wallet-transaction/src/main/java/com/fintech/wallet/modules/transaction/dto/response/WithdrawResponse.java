package com.fintech.wallet.modules.transaction.dto.response;

import com.fintech.wallet.modules.transaction.enums.TransactionStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class WithdrawResponse {
    private UUID transactionId;
    private UUID accountId;
    private BigDecimal amount;
    private TransactionStatus status;
    private OffsetDateTime createdAt;
}
