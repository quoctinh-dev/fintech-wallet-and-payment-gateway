package com.fintech.wallet.modules.transaction.service;

import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.grpc.account.AccountGrpcServiceGrpc.AccountGrpcServiceBlockingStub;
import com.fintech.wallet.grpc.account.AccountResponse;
import com.fintech.wallet.grpc.ledger.BalanceResponse;
import com.fintech.wallet.grpc.ledger.LedgerGrpcServiceGrpc.LedgerGrpcServiceBlockingStub;
import com.fintech.wallet.grpc.ledger.RecordEntryRequest;
import com.fintech.wallet.grpc.ledger.RecordEntryResponse;
import com.fintech.wallet.modules.transaction.dto.request.TransferRequest;
import com.fintech.wallet.modules.transaction.dto.response.TransferResponse;
import com.fintech.wallet.modules.transaction.enums.TransactionStatus;
import com.fintech.wallet.modules.transaction.repository.TransactionRepository;
import com.fintech.wallet.modules.transaction.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransferServiceIntegrationTest {

    @Autowired
    private TransactionServiceImpl transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID accountAId;
    private UUID accountBId;
    private UUID userId;

    private AccountGrpcServiceBlockingStub mockAccountStub;
    private LedgerGrpcServiceBlockingStub mockLedgerStub;

    private AtomicReference<Double> balanceA;

    @BeforeEach
    void setUp() {
        accountAId = UUID.randomUUID();
        accountBId = UUID.randomUUID();
        userId = UUID.randomUUID();
        balanceA = new AtomicReference<>(500000.0); // Khởi tạo 500k cho A

        mockAccountStub = Mockito.mock(AccountGrpcServiceBlockingStub.class);
        mockLedgerStub = Mockito.mock(LedgerGrpcServiceBlockingStub.class);

        ReflectionTestUtils.setField(transactionService, "accountGrpcClient", mockAccountStub);
        ReflectionTestUtils.setField(transactionService, "ledgerGrpcClient", mockLedgerStub);

        // Giả lập Account gRPC
        AccountResponse fakeAccountA = AccountResponse.newBuilder().setId(accountAId.toString()).setUserId(userId.toString()).build();
        AccountResponse fakeAccountB = AccountResponse.newBuilder().setId(accountBId.toString()).setUserId(UUID.randomUUID().toString()).build();

        Mockito.when(mockAccountStub.getAccountWithLock(any())).thenAnswer(inv -> {
            String id = ((com.fintech.wallet.grpc.account.GetAccountRequest) inv.getArgument(0)).getAccountId();
            return id.equals(accountAId.toString()) ? fakeAccountA : fakeAccountB;
        });

        // Giả lập Ledger lấy số dư
        Mockito.when(mockLedgerStub.getAccountBalance(any())).thenAnswer(inv -> {
            String id = ((com.fintech.wallet.grpc.ledger.GetBalanceRequest) inv.getArgument(0)).getAccountId();
            return BalanceResponse.newBuilder().setBalance(id.equals(accountAId.toString()) ? balanceA.get() : 0.0).build();
        });

        // Giả lập Ledger ghi số dư
        Mockito.when(mockLedgerStub.recordEntry(any())).thenReturn(RecordEntryResponse.newBuilder().setSuccess(true).build());
    }

    @Test
    @DisplayName("Pass Criteria 1: Sau chuyển khoản, gọi gRPC 2 lần tới Ledger")
    void transfer_Success_CallsLedgerTwice() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(accountAId);
        request.setToAccountId(accountBId);
        request.setAmount(new BigDecimal("100000.00"));
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setDescription("Test chuyển khoản");

        TransferResponse response = transactionService.transfer(request);

        // Kiểm tra xem gRPC recordEntry có được gọi đúng 2 lần (1 trừ, 1 cộng) không?
        verify(mockLedgerStub, times(2)).recordEntry(any(RecordEntryRequest.class));
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
    }

    @Test
    @DisplayName("Pass Criteria 2: Số dư không đủ -> BusinessException, không gọi Ledger")
    void transfer_InsufficientBalance_ShouldThrowAndNotCallLedger() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(accountAId);
        request.setToAccountId(accountBId);
        request.setAmount(new BigDecimal("999999.00")); // Vượt quá 500k
        request.setIdempotencyKey(UUID.randomUUID().toString());

        assertThrows(BusinessException.class, () -> transactionService.transfer(request));

        // Xác nhận gRPC ghi sổ KHÔNG BAO GIỜ được gọi
        verify(mockLedgerStub, times(0)).recordEntry(any());
    }

    @Test
    @DisplayName("Pass Criteria 3: Gọi 2 lần cùng idempotencyKey -> reject")
    void transfer_DuplicateIdempotencyKey_ShouldThrow() {
        String sameKey = UUID.randomUUID().toString();

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(accountAId);
        request.setToAccountId(accountBId);
        request.setAmount(new BigDecimal("50000.00"));
        request.setIdempotencyKey(sameKey);

        transactionService.transfer(request);

        TransferRequest duplicateRequest = new TransferRequest();
        duplicateRequest.setFromAccountId(accountAId);
        duplicateRequest.setToAccountId(accountBId);
        duplicateRequest.setAmount(new BigDecimal("50000.00"));
        duplicateRequest.setIdempotencyKey(sameKey);

        assertThrows(BusinessException.class, () -> transactionService.transfer(duplicateRequest));
    }

    @Test
    @DisplayName("Pass Criteria 4: Không thể chuyển cho chính mình")
    void transfer_ToSameAccount_ShouldThrow() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(accountAId);
        request.setToAccountId(accountAId);
        request.setAmount(new BigDecimal("10000.00"));
        request.setIdempotencyKey(UUID.randomUUID().toString());

        assertThrows(BusinessException.class, () -> transactionService.transfer(request));
    }

    @Test
    @DisplayName("Pass Criteria 5: Transaction được lưu với status SUCCESS")
    void transfer_Success_TransactionStatusIsSuccess() {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(accountAId);
        request.setToAccountId(accountBId);
        request.setAmount(new BigDecimal("100000.00"));
        request.setIdempotencyKey(UUID.randomUUID().toString());

        TransferResponse response = transactionService.transfer(request);
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());

        var txInDb = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        assertTrue(txInDb.isPresent());
        assertEquals(TransactionStatus.SUCCESS, txInDb.get().getStatus());
    }
}

