package com.fintech.wallet.modules.account.config;

import com.fintech.wallet.config.SystemConfig;
import com.fintech.wallet.modules.account.entity.Account;
import com.fintech.wallet.modules.account.enums.AccountType;
import com.fintech.wallet.modules.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SystemAccountInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final SystemConfig systemConfig;

    @Override
    public void run(String... args) {
        initSystemAccount(AccountType.SYSTEM_MASTER);
        initSystemAccount(AccountType.BANK_CLEARING);
        initSystemAccount(AccountType.SYSTEM_FEE);
    }

    private void initSystemAccount(AccountType accountType) {
        accountRepository.findByUserIdAndAccountType(systemConfig.getUserId(), accountType)
                .orElseGet(() -> {
                    Account systemAccount = Account.builder()
                            .userId(systemConfig.getUserId())
                            .accountType(accountType)
                            .currency(systemConfig.getDefaultCurrency())
                            .build();
                    Account saved = accountRepository.save(systemAccount);
                    log.info(">>> Khởi tạo thành công Ví Hệ Thống: {} với ID: {}", accountType, saved.getId());
                    return saved;
                });
    }
}
