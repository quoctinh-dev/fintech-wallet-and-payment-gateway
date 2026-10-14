package com.fintech.wallet.modules.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.grpc.account.AccountGrpcServiceGrpc.AccountGrpcServiceBlockingStub;
import com.fintech.wallet.grpc.account.AccountResponse;
import com.fintech.wallet.grpc.account.GetAccountRequest;
import com.fintech.wallet.grpc.ledger.BalanceResponse;
import com.fintech.wallet.grpc.ledger.GetBalanceRequest;
import com.fintech.wallet.grpc.ledger.LedgerGrpcServiceGrpc.LedgerGrpcServiceBlockingStub;
import com.fintech.wallet.modules.transaction.dto.request.TransferRequest;
import com.fintech.wallet.modules.transaction.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransferControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionServiceImpl transactionService;

    private UUID accountAId;
    private UUID accountBId;
    private UUID userId;

    private AccountGrpcServiceBlockingStub mockAccountStub;
    private LedgerGrpcServiceBlockingStub mockLedgerStub;

    @BeforeEach
    void setUp() {
        accountAId = UUID.randomUUID();
        accountBId = UUID.randomUUID();
        userId = UUID.randomUUID();

        // 1. Tạo ra 2 cái gRPC Client giả bằng Mockito
        mockAccountStub = Mockito.mock(AccountGrpcServiceBlockingStub.class);
        mockLedgerStub = Mockito.mock(LedgerGrpcServiceBlockingStub.class);

        // 2. Lén lút "tiêm" 2 cái Client giả này vào bên trong TransactionService
        ReflectionTestUtils.setField(transactionService, "accountGrpcClient", mockAccountStub);
        ReflectionTestUtils.setField(transactionService, "ledgerGrpcClient", mockLedgerStub);

        // 3. Dạy cho Client giả cách trả lời khi bị gọi GetAccountWithLock
        AccountResponse fakeAccountA = AccountResponse.newBuilder()
                .setId(accountAId.toString())
                .setUserId(userId.toString()) // Account A là của User đang đăng nhập
                .build();
        AccountResponse fakeAccountB = AccountResponse.newBuilder()
                .setId(accountBId.toString())
                .setUserId(UUID.randomUUID().toString())
                .build();

        Mockito.when(mockAccountStub.getAccountWithLock(GetAccountRequest.newBuilder().setAccountId(accountAId.toString()).build()))
                .thenReturn(fakeAccountA);
        Mockito.when(mockAccountStub.getAccountWithLock(GetAccountRequest.newBuilder().setAccountId(accountBId.toString()).build()))
                .thenReturn(fakeAccountB);

        // 4. Dạy cho Client giả cách trả lời khi bị gọi GetAccountBalance (Trả về 500k)
        BalanceResponse fakeBalance = BalanceResponse.newBuilder().setBalance(500000.0).build();
        Mockito.when(mockLedgerStub.getAccountBalance(GetBalanceRequest.newBuilder().setAccountId(accountAId.toString()).build()))
                .thenReturn(fakeBalance);

        // 5. Hàm RecordEntry không trả về gì quan trọng, cứ cho qua
        Mockito.when(mockLedgerStub.recordEntry(any())).thenReturn(null);
    }

    @Test
    @DisplayName("API: Chuyển khoản thành công -> 200 OK + success=true")
    void transfer_Success_Returns200() throws Exception {
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(accountAId);
        request.setToAccountId(accountBId);
        request.setAmount(new BigDecimal("100000.00"));
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setDescription("Test API");

        // Gọi API bằng MockMvc, truyền kèm userId giả để khớp với tài khoản A
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("userId", userId.toString())) // Tùy thuộc vào cách bạn config JWT Security Filter
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
