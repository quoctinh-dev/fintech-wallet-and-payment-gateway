package com.fintech.wallet.modules.ledger.service;

import com.fintech.wallet.modules.ledger.dto.LedgerEntryResponse;
import com.fintech.wallet.modules.ledger.dto.ReconciliationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerService {
    void recordEntry(UUID transactionId, UUID accountId, BigDecimal amount);
    BigDecimal getAccountBalance(UUID accountId);
    ReconciliationResponse reconcileLedger();
    Page<LedgerEntryResponse> getLedgerEntries(UUID accountId, Pageable pageable);
}
