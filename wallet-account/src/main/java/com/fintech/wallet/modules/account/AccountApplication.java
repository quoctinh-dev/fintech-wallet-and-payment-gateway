package com.fintech.wallet.modules.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(scanBasePackages = {
        "com.fintech.wallet.modules.account",
        "com.fintech.wallet.config",
        "com.fintech.wallet.common"
})

@EntityScan(basePackages = "com.fintech.wallet.modules.account.entity")
@EnableJpaRepositories(basePackages = "com.fintech.wallet.modules.account.repository")
@EnableMethodSecurity
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }
}
