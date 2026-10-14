package com.fintech.wallet.modules.account.service.impl;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.common.dto.PaginationResponse;
import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.common.exception.ErrorCode;
import com.fintech.wallet.grpc.ledger.GetBalanceRequest;
import com.fintech.wallet.grpc.ledger.LedgerGrpcServiceGrpc;
import com.fintech.wallet.modules.account.dto.request.CreateAccountRequest;
import com.fintech.wallet.modules.account.dto.request.UpdateAccountStatusRequest;
import com.fintech.wallet.modules.account.dto.response.AccountResponse;
import com.fintech.wallet.modules.account.dto.response.AccountSearchResponse;
import com.fintech.wallet.modules.account.entity.Account;
import com.fintech.wallet.modules.account.enums.AccountStatus;
import com.fintech.wallet.modules.account.enums.AccountType;
import com.fintech.wallet.modules.account.mapper.AccountMapper;
import com.fintech.wallet.modules.account.repository.AccountRepository;
import com.fintech.wallet.modules.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final RestTemplate restTemplate;
    private final DiscoveryClient discoveryClient;

    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    private static final String GATEWAY_SECRET_VALUE = "fintech-secret-key-2026";

    @GrpcClient("wallet-ledger")
    private LedgerGrpcServiceGrpc.LedgerGrpcServiceBlockingStub ledgerGrpcClient;

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        accountRepository.findByUserIdAndAccountType(request.getUserId(), request.getAccountType())
                .ifPresent(account -> {
                    throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
                });

        Account account = Account.builder()
                .userId(request.getUserId())
                .accountType(request.getAccountType())
                .status(AccountStatus.ACTIVE)
                .currency(request.getCurrency())
                .build();

        Account savedAccount = accountRepository.save(account);
        return accountMapper.toResponse(savedAccount, BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResponse<AccountResponse> getAccounts(AccountStatus status, AccountType accountType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Account> accountPage = accountRepository.searchAccounts(status, accountType, pageable);

        Page<AccountResponse> responsePage = accountPage.map(account -> {
            BigDecimal balance = getBalanceFromLedger(account.getId());
            return accountMapper.toResponse(account, balance);
        });

        return PaginationResponse.fromPage(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        BigDecimal balance = getBalanceFromLedger(account.getId());
        return accountMapper.toResponse(account, balance);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountByUserIdAndType(UUID userId, AccountType accountType) {
        Account account = accountRepository.findByUserIdAndAccountType(userId, accountType)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        BigDecimal balance = getBalanceFromLedger(account.getId());
        return accountMapper.toResponse(account, balance);
    }

    @Override
    @Transactional
    public AccountResponse getAccountWithLock(UUID accountId) {
        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        return accountMapper.toResponse(account, BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountSearchResponse searchRecipientByPhone(String phoneNumber, String callerId, String authHeader) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty() || !phoneNumber.matches("^(0|\\+84)[0-9]{9,10}$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 1. Gọi wallet-auth để lấy thông tin người dùng theo số điện thoại (gắn kèm Gateway Secret)
        String authBaseUrl = getAuthServiceBaseUrl();
        HttpHeaders headers = new HttpHeaders();
        headers.set(GATEWAY_SECRET_HEADER, GATEWAY_SECRET_VALUE);
        if (callerId != null) {
            headers.set("X-User-Id", callerId);
        }
        headers.set("X-User-Roles", "ROLE_USER");
        if (authHeader != null && !authHeader.isEmpty()) {
            headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        }
        HttpEntity<?> entity = new HttpEntity<>(headers);

        ApiResponse<Map<String, Object>> userApiResponse;
        try {
            ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                    authBaseUrl + "/api/v1/users/recipient?phoneNumber=" + phoneNumber,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {}
            );
            userApiResponse = response.getBody();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }

        if (userApiResponse == null || !userApiResponse.isSuccess() || userApiResponse.getData() == null) {
            throw new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }

        Map<String, Object> userData = userApiResponse.getData();
        String recipientUserIdStr = (String) userData.get("id");
        String fullName = (String) userData.get("fullName");
        UUID recipientUserId = UUID.fromString(recipientUserIdStr);

        // 2. Chặn tự tra cứu chính mình
        if (callerId != null && recipientUserId.equals(UUID.fromString(callerId))) {
            throw new BusinessException(ErrorCode.CANNOT_TRANSFER_TO_SELF);
        }

        // 3. Tìm ví USER_WALLET của người nhận
        Account recipientAccount = accountRepository.findByUserIdAndAccountType(recipientUserId, AccountType.USER_WALLET)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND));

        // 4. Kiểm tra trạng thái ví
        if (recipientAccount.getStatus() == AccountStatus.FROZEN) {
            throw new BusinessException(ErrorCode.ACCOUNT_FROZEN);
        }
        if (recipientAccount.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ACCOUNT_CLOSED);
        }

        return AccountSearchResponse.builder()
                .accountId(recipientAccount.getId())
                .userId(recipientUserId)
                .fullName(fullName)
                .phoneNumber(phoneNumber)
                .currency(recipientAccount.getCurrency())
                .status(recipientAccount.getStatus())
                .build();
    }

    @Override
    @Transactional
    public AccountResponse updateAccountStatus(UUID accountId, UpdateAccountStatusRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (request.getStatus() == null ||
                (request.getStatus() != AccountStatus.ACTIVE && request.getStatus() != AccountStatus.FROZEN)) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_STATUS);
        }

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ACCOUNT_CLOSED);
        }

        account.setStatus(request.getStatus());
        Account savedAccount = accountRepository.save(account);

        BigDecimal balance = getBalanceFromLedger(savedAccount.getId());
        return accountMapper.toResponse(savedAccount, balance);
    }

    @Override
    @Transactional
    public void closeAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessException(ErrorCode.ACCOUNT_CLOSED);
        }

        BigDecimal balance = getBalanceFromLedger(account.getId());
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_CANNOT_BE_CLOSED_WITH_BALANCE);
        }

        account.setStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
    }

    private BigDecimal getBalanceFromLedger(UUID accountId) {
        try {
            double balanceValue = ledgerGrpcClient.getAccountBalance(
                    GetBalanceRequest.newBuilder().setAccountId(accountId.toString()).build()
            ).getBalance();
            return BigDecimal.valueOf(balanceValue);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String getAuthServiceBaseUrl() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances("wallet-auth");
            if (instances != null && !instances.isEmpty()) {
                return instances.get(0).getUri().toString();
            }
        } catch (Exception ignored) {
        }
        return "http://localhost:8081";
    }
}
