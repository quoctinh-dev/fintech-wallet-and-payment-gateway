package com.fintech.wallet.modules.transaction.service.impl;

import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.common.exception.ErrorCode;
import com.fintech.wallet.common.outbox.entity.OutboxEvent;
import com.fintech.wallet.common.outbox.repository.OutboxEventRepository;
import com.fintech.wallet.common.security.RedisIdempotencyService;
import com.fintech.wallet.config.SystemConfig;
import com.fintech.wallet.grpc.account.AccountGrpcServiceGrpc;
import com.fintech.wallet.grpc.account.AccountResponse;
import com.fintech.wallet.grpc.account.GetAccountByUserRequest;
import com.fintech.wallet.grpc.account.GetAccountRequest;
import com.fintech.wallet.grpc.ledger.GetAccountTransactionsRequest;
import com.fintech.wallet.grpc.ledger.GetAccountTransactionsResponse;
import com.fintech.wallet.grpc.ledger.GetBalanceRequest;
import com.fintech.wallet.grpc.ledger.LedgerGrpcServiceGrpc;
import com.fintech.wallet.grpc.ledger.RecordEntryRequest;
import com.fintech.wallet.modules.transaction.dto.request.DepositRequest;
import com.fintech.wallet.modules.transaction.dto.request.TransferRequest;
import com.fintech.wallet.modules.transaction.dto.request.WithdrawRequest;
import com.fintech.wallet.modules.transaction.dto.response.TransactionHistoryResponse;
import com.fintech.wallet.modules.transaction.dto.response.TransactionResponse;
import com.fintech.wallet.modules.transaction.dto.response.TransferResponse;
import com.fintech.wallet.modules.transaction.dto.response.WithdrawResponse;
import com.fintech.wallet.modules.transaction.entity.Transaction;
import com.fintech.wallet.modules.transaction.enums.TransactionStatus;
import com.fintech.wallet.modules.transaction.repository.TransactionRepository;
import com.fintech.wallet.modules.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final SystemConfig systemConfig;
    private final RedisIdempotencyService redisIdempotencyService;
    private final OutboxEventRepository outboxEventRepository;

    @GrpcClient("wallet-account")
    private AccountGrpcServiceGrpc.AccountGrpcServiceBlockingStub accountGrpcClient;

    @GrpcClient("wallet-ledger")
    private LedgerGrpcServiceGrpc.LedgerGrpcServiceBlockingStub ledgerGrpcClient;

    @Override
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        boolean acquired = redisIdempotencyService.lockKey(request.getIdempotencyKey(), 120);
        if (!acquired) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_DUPLICATED);
        }

        try {
            transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(tx -> {
                        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_DUPLICATED);
                    });

            UUID fromId = request.getFromAccountId();
            UUID toId = request.getToAccountId();

            UUID firstLockId = fromId.compareTo(toId) < 0 ? fromId : toId;
            UUID secondLockId = fromId.compareTo(toId) < 0 ? toId : fromId;

            AccountResponse firstAccount = accountGrpcClient.getAccountWithLock(
                    GetAccountRequest.newBuilder()
                            .setAccountId(firstLockId.toString())
                            .build()
            );

            AccountResponse secondAccount = accountGrpcClient.getAccountWithLock(
                    GetAccountRequest.newBuilder()
                            .setAccountId(secondLockId.toString())
                            .build()
            );

            AccountResponse fromAccount = firstLockId.equals(fromId) ? firstAccount : secondAccount;
            AccountResponse toAccount = firstLockId.equals(toId) ? firstAccount : secondAccount;

            UUID currentUserId = extractCurrentUserId();

            if (!UUID.fromString(fromAccount.getUserId()).equals(currentUserId)) {
                log.warn("[Security Alert] User {} cố tình chuyển tiền từ ví {}", currentUserId, fromAccount.getId());
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }

            double fromBalance = ledgerGrpcClient.getAccountBalance(
                    GetBalanceRequest.newBuilder()
                            .setAccountId(fromAccount.getId())
                            .build()
            ).getBalance();

            if (BigDecimal.valueOf(fromBalance).compareTo(request.getAmount()) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }

            Transaction transaction = Transaction.builder()
                    .idempotencyKey(request.getIdempotencyKey())
                    .description(request.getDescription() != null ? request.getDescription() : "Chuyển khoản")
                    .accountId(UUID.fromString(fromAccount.getId()))
                    .amount(request.getAmount())
                    .status(TransactionStatus.SUCCESS)
                    .build();

            Transaction saved = transactionRepository.save(transaction);

            ledgerGrpcClient.recordEntry(
                    RecordEntryRequest.newBuilder()
                            .setTransactionId(saved.getId().toString())
                            .setAccountId(fromAccount.getId())
                            .setAmount(request.getAmount().negate().doubleValue())
                            .build()
            );

            ledgerGrpcClient.recordEntry(
                    RecordEntryRequest.newBuilder()
                            .setTransactionId(saved.getId().toString())
                            .setAccountId(toAccount.getId())
                            .setAmount(request.getAmount().doubleValue())
                            .build()
            );

            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(saved.getId().toString())
                    .topic("transaction-events")
                    .payload(String.format("{\"accountId\":\"%s\",\"type\":\"TRANSFER\",\"amount\":%s}", fromAccount.getId(), request.getAmount()))
                    .status("PENDING")
                    .build();
            outboxEventRepository.save(outbox);

            return TransferResponse.builder()
                    .transactionId(saved.getId())
                    .fromAccountId(UUID.fromString(fromAccount.getId()))
                    .toAccountId(UUID.fromString(toAccount.getId()))
                    .amount(request.getAmount())
                    .status(saved.getStatus())
                    .description(saved.getDescription())
                    .createdAt(saved.getCreatedAt())
                    .build();

        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            redisIdempotencyService.unlockKey(request.getIdempotencyKey());
            throw ex;
        }
    }

    @Override
    @Transactional
    public WithdrawResponse withdraw(WithdrawRequest request) {
        UUID currentUserId = extractCurrentUserId();

        AccountResponse userAccountUnchecked = accountGrpcClient.getAccountById(
                GetAccountRequest.newBuilder()
                        .setAccountId(request.getAccountId().toString())
                        .build()
        );

        if (!UUID.fromString(userAccountUnchecked.getUserId()).equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        boolean isLocked = redisIdempotencyService.lockKey(request.getIdempotencyKey(), 120);
        if (!isLocked) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_DUPLICATED);
        }

        try {
            transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(tx -> {
                        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_DUPLICATED);
                    });

            AccountResponse clearingAccountUnchecked = accountGrpcClient.getAccountByUserIdAndType(
                    GetAccountByUserRequest.newBuilder()
                            .setUserId(systemConfig.getUserId().toString())
                            .setAccountType("BANK_CLEARING")
                            .build()
            );

            UUID userAccountId = UUID.fromString(userAccountUnchecked.getId());
            UUID clearingAccountId = UUID.fromString(clearingAccountUnchecked.getId());

            UUID firstLockId = userAccountId.compareTo(clearingAccountId) < 0 ? userAccountId : clearingAccountId;
            UUID secondLockId = userAccountId.compareTo(clearingAccountId) < 0 ? clearingAccountId : userAccountId;

            AccountResponse firstAccount = accountGrpcClient.getAccountWithLock(
                    GetAccountRequest.newBuilder()
                            .setAccountId(firstLockId.toString())
                            .build()
            );

            AccountResponse secondAccount = accountGrpcClient.getAccountWithLock(
                    GetAccountRequest.newBuilder()
                            .setAccountId(secondLockId.toString())
                            .build()
            );

            AccountResponse userAccount = firstLockId.equals(userAccountId) ? firstAccount : secondAccount;
            AccountResponse clearingAccount = firstLockId.equals(clearingAccountId) ? firstAccount : secondAccount;

            double currentBalance = ledgerGrpcClient.getAccountBalance(
                    GetBalanceRequest.newBuilder()
                            .setAccountId(userAccount.getId())
                            .build()
            ).getBalance();

            if (BigDecimal.valueOf(currentBalance).compareTo(request.getAmount()) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }

            Transaction transaction = Transaction.builder()
                    .accountId(UUID.fromString(userAccount.getId()))
                    .amount(request.getAmount())
                    .idempotencyKey(request.getIdempotencyKey())
                    .status(TransactionStatus.SUCCESS)
                    .description("Rút tiền")
                    .build();

            Transaction savedTransaction = transactionRepository.save(transaction);

            ledgerGrpcClient.recordEntry(
                    RecordEntryRequest.newBuilder()
                            .setTransactionId(savedTransaction.getId().toString())
                            .setAccountId(userAccount.getId())
                            .setAmount(request.getAmount().negate().doubleValue())
                            .build()
            );

            ledgerGrpcClient.recordEntry(
                    RecordEntryRequest.newBuilder()
                            .setTransactionId(savedTransaction.getId().toString())
                            .setAccountId(clearingAccount.getId())
                            .setAmount(request.getAmount().doubleValue())
                            .build()
            );

            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateId(savedTransaction.getId().toString())
                    .topic("transaction-events")
                    .payload(String.format("{\"accountId\":\"%s\",\"type\":\"WITHDRAW\",\"amount\":%s}", userAccount.getId(), request.getAmount()))
                    .status("PENDING")
                    .build();
            outboxEventRepository.save(outbox);

            return WithdrawResponse.builder()
                    .transactionId(savedTransaction.getId())
                    .accountId(UUID.fromString(userAccount.getId()))
                    .amount(savedTransaction.getAmount())
                    .status(savedTransaction.getStatus())
                    .createdAt(savedTransaction.getCreatedAt())
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            redisIdempotencyService.unlockKey(request.getIdempotencyKey());
            throw e;
        }
    }

    @Override
    @Transactional
    public void createPendingDeposit(DepositRequest request) {
        AccountResponse userAccount = accountGrpcClient.getAccountById(
                GetAccountRequest.newBuilder()
                        .setAccountId(request.getAccountId().toString())
                        .build()
        );

        transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(tx -> {
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_DUPLICATED);
                });

        Transaction transaction = Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .description("Nạp tiền")
                .accountId(UUID.fromString(userAccount.getId()))
                .amount(request.getAmount())
                .status(TransactionStatus.PENDING)
                .build();

        transactionRepository.save(transaction);
    }

    @Override
    @Transactional
    public void confirmDeposit(String idempotencyKey) {
        Transaction transaction = transactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));

        if (TransactionStatus.SUCCESS.equals(transaction.getStatus())
                || TransactionStatus.FAILED.equals(transaction.getStatus())) {
            return;
        }

        transaction.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(transaction);

        AccountResponse bankClearingAccount = accountGrpcClient.getAccountByUserIdAndType(
                GetAccountByUserRequest.newBuilder()
                        .setUserId(systemConfig.getUserId().toString())
                        .setAccountType("BANK_CLEARING")
                        .build()
        );

        ledgerGrpcClient.recordEntry(
                RecordEntryRequest.newBuilder()
                        .setTransactionId(transaction.getId().toString())
                        .setAccountId(bankClearingAccount.getId())
                        .setAmount(transaction.getAmount().negate().doubleValue())
                        .build()
        );

        ledgerGrpcClient.recordEntry(
                RecordEntryRequest.newBuilder()
                        .setTransactionId(transaction.getId().toString())
                        .setAccountId(transaction.getAccountId().toString())
                        .setAmount(transaction.getAmount().doubleValue())
                        .build()
        );

        OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(transaction.getId().toString())
                .topic("transaction-events")
                .payload(String.format("{\"accountId\":\"%s\",\"type\":\"DEPOSIT\",\"amount\":%s}", transaction.getAccountId(), transaction.getAmount()))
                .status("PENDING")
                .build();
        outboxEventRepository.save(outbox);
    }

    @Override
    @Transactional
    public void failDeposit(String idempotencyKey) {
        Transaction transaction = transactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));

        if (TransactionStatus.PENDING.equals(transaction.getStatus())) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> getTransactionHistory(UUID accountId, Pageable pageable) {
        AccountResponse account = accountGrpcClient.getAccountById(
                GetAccountRequest.newBuilder()
                        .setAccountId(accountId.toString())
                        .build()
        );

        UUID currentUserId = extractCurrentUserId();

        boolean isPrivileged = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().contains("ADMIN") || a.getAuthority().contains("AUDITOR"));

        if (!isPrivileged && !UUID.fromString(account.getUserId()).equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        GetAccountTransactionsRequest request = GetAccountTransactionsRequest.newBuilder()
                .setAccountId(accountId.toString())
                .setPage(pageable.getPageNumber())
                .setSize(pageable.getPageSize())
                .build();

        GetAccountTransactionsResponse response = ledgerGrpcClient.getAccountTransactions(request);

        if (response.getEntriesList().isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> transactionIds = response.getEntriesList().stream()
                .map(entry -> UUID.fromString(entry.getTransactionId()))
                .toList();

        List<Transaction> transactions = transactionRepository.findTransactionsByIds(transactionIds);

        Map<UUID, Transaction> transactionMap = transactions.stream()
                .collect(Collectors.toMap(Transaction::getId, transaction -> transaction));

        List<TransactionHistoryResponse> content = response.getEntriesList().stream()
                .map(entry -> {
                    UUID transactionId = UUID.fromString(entry.getTransactionId());
                    Transaction transaction = transactionMap.get(transactionId);

                    return new TransactionHistoryResponse(
                            transactionId,
                            BigDecimal.valueOf(entry.getAmount()),
                            transaction != null ? transaction.getDescription() : "Không xác định",
                            transaction != null ? transaction.getStatus() : TransactionStatus.FAILED,
                            transaction != null ? transaction.getCreatedAt() : OffsetDateTime.now()
                    );
                })
                .toList();

        return new PageImpl<>(content, pageable, response.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAllTransactions(TransactionStatus status, Pageable pageable) {
        Page<Transaction> page = transactionRepository.searchTransactions(status, pageable);
        return page.map(this::mapToTransactionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        return mapToTransactionResponse(transaction);
    }

    private TransactionResponse mapToTransactionResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccountId())
                .amount(transaction.getAmount())
                .idempotencyKey(transaction.getIdempotencyKey())
                .description(transaction.getDescription())
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private UUID extractCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return (principal instanceof UUID) ? (UUID) principal : UUID.fromString((String) principal);
    }
}