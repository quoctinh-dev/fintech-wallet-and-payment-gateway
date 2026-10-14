package com.fintech.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
@EnableScheduling
@EntityScan(basePackages = {
        "com.fintech.wallet.modules.transaction.entity",
        "com.fintech.wallet.common.outbox.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.fintech.wallet.modules.transaction.repository",
        "com.fintech.wallet.common.outbox.repository"
})
public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}
