package com.fintech.wallet.modules.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryResponse {
    private UUID id;
    private UUID transactionId;
    private UUID accountId;
    private BigDecimal amount;
    private OffsetDateTime createdAt;
}
