package com.fintech.wallet.modules.account.service;

import com.fintech.wallet.common.dto.PaginationResponse;
import com.fintech.wallet.modules.account.dto.request.CreateAccountRequest;
import com.fintech.wallet.modules.account.dto.request.UpdateAccountStatusRequest;
import com.fintech.wallet.modules.account.dto.response.AccountResponse;
import com.fintech.wallet.modules.account.dto.response.AccountSearchResponse;
import com.fintech.wallet.modules.account.enums.AccountStatus;
import com.fintech.wallet.modules.account.enums.AccountType;

import java.util.UUID;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    PaginationResponse<AccountResponse> getAccounts(AccountStatus status, AccountType accountType, int page, int size);

    AccountResponse getAccountById(UUID accountId);

    AccountResponse getAccountByUserIdAndType(UUID userId, AccountType accountType);

    AccountResponse getAccountWithLock(UUID accountId);

    AccountSearchResponse searchRecipientByPhone(String phoneNumber, String callerId, String authHeader);

    AccountResponse updateAccountStatus(UUID accountId, UpdateAccountStatusRequest request);

    void closeAccount(UUID accountId);
}
