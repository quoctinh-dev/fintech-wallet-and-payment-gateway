package com.fintech.wallet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@org.springframework.context.annotation.Configuration
@ConfigurationProperties(prefix = "vnpay")
public class VNPayConfig {
    private String payUrl;
    private String returnUrl;
    private String tmnCode;
    private String secretKey;
    private String version;
    private String command;
    private Integer expireMinutes;
}
