package com.fintech.wallet.modules.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Lớp ánh xạ cấu hình cho dịch vụ gửi thư điện tử Brevo.
 * Trích xuất các tham số (API Key, Thông tin người gửi) từ application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "brevo")
@Data
public class BrevoConfig {

    private String apiKey;
    private String senderEmail;
    private String senderName;
}
