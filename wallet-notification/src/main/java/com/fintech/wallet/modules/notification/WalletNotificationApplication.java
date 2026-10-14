package com.fintech.wallet.modules.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.fintech.wallet.modules.notification",
        "com.fintech.wallet.common",
        "com.fintech.wallet.config"
})
public class WalletNotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(WalletNotificationApplication.class, args);
    }
}
