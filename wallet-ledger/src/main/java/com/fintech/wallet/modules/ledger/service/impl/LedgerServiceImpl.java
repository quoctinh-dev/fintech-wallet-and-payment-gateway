package com.fintech.wallet.modules.ledger.service.impl;

import com.fintech.wallet.modules.ledger.dto.LedgerEntryResponse;
import com.fintech.wallet.modules.ledger.dto.ReconciliationResponse;
import com.fintech.wallet.modules.ledger.entity.LedgerEntry;
import com.fintech.wallet.modules.ledger.repository.LedgerEntryRepository;
import com.fintech.wallet.modules.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional
    public void recordEntry(UUID transactionId, UUID accountId, BigDecimal amount) {
        LedgerEntry entry = LedgerEntry.builder()
                .transactionId(transactionId)
                .accountId(accountId)
                .amount(amount)
                .build();

        ledgerEntryRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAccountBalance(UUID accountId) {
        BigDecimal balance = ledgerEntryRepository.getBalanceByAccountId(accountId);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationResponse reconcileLedger() {
        BigDecimal totalDelta = ledgerEntryRepository.getTotalLedgerBalance();
        long totalEntries = ledgerEntryRepository.getTotalEntriesCount();

        boolean isBalanced = totalDelta.compareTo(BigDecimal.ZERO) == 0;

        return ReconciliationResponse.builder()
                .balanced(isBalanced)
                .totalDelta(totalDelta)
                .totalEntries(totalEntries)
                .status(isBalanced ? "BALANCED_ZERO_ERROR" : "UNBALANCED_CRITICAL_ERROR")
                .checkedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getLedgerEntries(UUID accountId, Pageable pageable) {
        Page<LedgerEntry> page = accountId != null
                ? ledgerEntryRepository.findByAccountId(accountId, pageable)
                : ledgerEntryRepository.findAll(pageable);

        return page.map(entry -> LedgerEntryResponse.builder()
                .id(entry.getId())
                .transactionId(entry.getTransactionId())
                .accountId(entry.getAccountId())
                .amount(entry.getAmount())
                .createdAt(entry.getCreatedAt())
                .build());
    }
}
