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
public class TransactionHistoryResponse {
    private UUID transactionId;
    private BigDecimal amount; // Âm là bị trừ tiền, Dương là được cộng tiền
    private String description;
    private TransactionStatus status;
    private OffsetDateTime createdAt;
}
