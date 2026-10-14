package com.fintech.wallet.modules.ledger.service;

import com.fintech.wallet.modules.ledger.entity.LedgerEntry;
import com.fintech.wallet.modules.ledger.repository.LedgerEntryRepository;
import com.fintech.wallet.modules.ledger.service.impl.LedgerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    private UUID accountId;
    private UUID transactionId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Tính tổng số dư tài khoản chính xác từ các dòng Ledger")
    void getAccountBalance_ShouldReturnCorrectBalance() {
        BigDecimal expectedBalance = new BigDecimal("500000.00");

        when(ledgerEntryRepository.getBalanceByAccountId(accountId)).thenReturn(expectedBalance);

        BigDecimal actualBalance = ledgerService.getAccountBalance(accountId);

        assertTrue(actualBalance != null && expectedBalance.compareTo(actualBalance) == 0,
                "Số dư trả về không đúng với kỳ vọng");
        verify(ledgerEntryRepository, times(1)).getBalanceByAccountId(accountId);
    }

    @Test
    @DisplayName("Trả về 0.00 khi tài khoản mới chưa có lịch sử Ledger")
    void getAccountBalance_WhenNull_ShouldReturnZero() {
        when(ledgerEntryRepository.getBalanceByAccountId(accountId)).thenReturn(null);

        BigDecimal actualBalance = ledgerService.getAccountBalance(accountId);

        assertTrue(BigDecimal.ZERO.compareTo(actualBalance) == 0,
                "Tài khoản mới phải có số dư bằng 0");
    }

    @Test
    @DisplayName("Ghi thành công một bút toán Nợ/Có vào Ledger")
    void recordEntry_ShouldSaveLedgerEntry() {
        BigDecimal amount = new BigDecimal("100000.00");
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(i -> i.getArgument(0));

        ledgerService.recordEntry(transactionId, accountId, amount);

        verify(ledgerEntryRepository, times(1)).save(any(LedgerEntry.class));
    }
}