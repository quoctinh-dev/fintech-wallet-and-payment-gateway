package com.fintech.wallet.modules.transaction.service;

import com.fintech.wallet.grpc.account.AccountGrpcServiceGrpc.AccountGrpcServiceBlockingStub;
import com.fintech.wallet.grpc.account.AccountResponse;
import com.fintech.wallet.grpc.ledger.BalanceResponse;
import com.fintech.wallet.grpc.ledger.LedgerGrpcServiceGrpc.LedgerGrpcServiceBlockingStub;
import com.fintech.wallet.grpc.ledger.RecordEntryRequest;
import com.fintech.wallet.grpc.ledger.RecordEntryResponse;
import com.fintech.wallet.modules.transaction.dto.request.TransferRequest;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ConcurrentTransferTest {

    @Autowired private TransactionServiceImpl transactionService;

    private UUID accountAId;
    private UUID accountBId;
    private UUID userId;

    private static final double INITIAL_BALANCE = 500000.00;
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("100000.00");

    private AtomicReference<Double> balanceA;
    private AtomicReference<Double> balanceB;

    @BeforeEach
    void setUp() {
        accountAId = UUID.randomUUID();
        accountBId = UUID.randomUUID();
        userId = UUID.randomUUID();

        balanceA = new AtomicReference<>(INITIAL_BALANCE);
        balanceB = new AtomicReference<>(0.0);

        AccountGrpcServiceBlockingStub mockAccountStub = Mockito.mock(AccountGrpcServiceBlockingStub.class);
        LedgerGrpcServiceBlockingStub mockLedgerStub = Mockito.mock(LedgerGrpcServiceBlockingStub.class);

        ReflectionTestUtils.setField(transactionService, "accountGrpcClient", mockAccountStub);
        ReflectionTestUtils.setField(transactionService, "ledgerGrpcClient", mockLedgerStub);

        // MOCK ACCOUNT
        AccountResponse fakeAccountA = AccountResponse.newBuilder().setId(accountAId.toString()).setUserId(userId.toString()).build();
        AccountResponse fakeAccountB = AccountResponse.newBuilder().setId(accountBId.toString()).setUserId(UUID.randomUUID().toString()).build();
        Mockito.when(mockAccountStub.getAccountWithLock(any())).thenAnswer(inv -> {
            String id = ((com.fintech.wallet.grpc.account.GetAccountRequest) inv.getArgument(0)).getAccountId();
            return id.equals(accountAId.toString()) ? fakeAccountA : fakeAccountB;
        });

        // MOCK LEDGER BALANCE (Lấy số dư thời gian thực từ biến Atomic)
        Mockito.when(mockLedgerStub.getAccountBalance(any())).thenAnswer(inv -> {
            String id = ((com.fintech.wallet.grpc.ledger.GetBalanceRequest) inv.getArgument(0)).getAccountId();
            double current = id.equals(accountAId.toString()) ? balanceA.get() : balanceB.get();
            return BalanceResponse.newBuilder().setBalance(current).build();
        });

        // MOCK LEDGER RECORD ENTRY (Cộng trừ tiền vào biến Atomic)
        Mockito.when(mockLedgerStub.recordEntry(any())).thenAnswer(inv -> {
            RecordEntryRequest req = inv.getArgument(0);
            if (req.getAccountId().equals(accountAId.toString())) {
                balanceA.updateAndGet(v -> v + req.getAmount());
            } else if (req.getAccountId().equals(accountBId.toString())) {
                balanceB.updateAndGet(v -> v + req.getAmount());
            }
            return RecordEntryResponse.newBuilder().setSuccess(true).build();
        });
    }

    @Test
    @DisplayName("Concurrent: 10 request đồng thời -> số dư không bao giờ âm")
    void concurrent_TransferFromA_BalanceNeverNegative() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String idempotencyKey = "concurrent-key-" + i + "-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    TransferRequest request = new TransferRequest();
                    request.setFromAccountId(accountAId);
                    request.setToAccountId(accountBId);
                    request.setAmount(TRANSFER_AMOUNT);
                    request.setIdempotencyKey(idempotencyKey);

                    transactionService.transfer(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Fail count
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        System.out.println("✅ Success: " + successCount.get());
        System.out.println("💰 Balance A: " + balanceA.get() + " | Balance B: " + balanceB.get());

        assertTrue(balanceA.get() >= 0, "🚨 BUG: Số dư A bị âm!");
        assertEquals(INITIAL_BALANCE, balanceA.get() + balanceB.get(), "🚨 BUG: Tổng tiền bị lệch!");
        assertTrue(successCount.get() <= 5, "🚨 BUG: Giao dịch vượt quá số tiền cho phép!");
    }
}
