package com.fintech.wallet.modules.transaction.service;

import com.fintech.wallet.modules.transaction.dto.request.DepositRequest;
import com.fintech.wallet.modules.transaction.dto.request.TransferRequest;
import com.fintech.wallet.modules.transaction.dto.request.WithdrawRequest;
import com.fintech.wallet.modules.transaction.dto.response.TransactionHistoryResponse;
import com.fintech.wallet.modules.transaction.dto.response.TransactionResponse;
import com.fintech.wallet.modules.transaction.dto.response.TransferResponse;
import com.fintech.wallet.modules.transaction.dto.response.WithdrawResponse;
import com.fintech.wallet.modules.transaction.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransactionService {

    void createPendingDeposit(DepositRequest request);

    void confirmDeposit(String idempotencyKey);

    void failDeposit(String idempotencyKey);

    TransferResponse transfer(TransferRequest request);

    WithdrawResponse withdraw(WithdrawRequest request);

    Page<TransactionHistoryResponse> getTransactionHistory(
            UUID accountId,
            Pageable pageable
    );

    Page<TransactionResponse> getAllTransactions(
            TransactionStatus status,
            Pageable pageable
    );

    TransactionResponse getTransactionById(UUID transactionId);
}
