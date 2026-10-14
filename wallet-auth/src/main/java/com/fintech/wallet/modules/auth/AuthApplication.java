package com.fintech.wallet.modules.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EntityScan(basePackages = {"com.fintech.wallet.modules.auth.entity", "com.fintech.wallet.common.outbox.entity"})
@EnableJpaRepositories(basePackages = {"com.fintech.wallet.modules.auth.repository", "com.fintech.wallet.common.outbox.repository"})
        @ComponentScan(basePackages = "com.fintech.wallet")
        @EnableMethodSecurity
        @EnableScheduling
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
