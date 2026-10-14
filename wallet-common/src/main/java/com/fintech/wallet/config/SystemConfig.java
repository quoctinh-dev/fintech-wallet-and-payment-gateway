package com.fintech.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

@Data
@org.springframework.context.annotation.Configuration
@ConfigurationProperties(prefix = "system")
public class SystemConfig {
    private UUID userId;
    private String defaultCurrency;
}