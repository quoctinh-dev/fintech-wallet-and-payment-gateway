package com.fintech.wallet.modules.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(scanBasePackages = {
        "com.fintech.wallet.modules.ledger",
        "com.fintech.wallet.config",
        "com.fintech.wallet.common"
})
@EntityScan(basePackages = "com.fintech.wallet.modules.ledger.entity")
@EnableJpaRepositories(basePackages = "com.fintech.wallet.modules.ledger.repository")
@EnableMethodSecurity
public class LedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
