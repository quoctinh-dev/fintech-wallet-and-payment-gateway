package com.fintech.wallet.modules.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.modules.notification.entity.AuditLog;
import com.fintech.wallet.modules.notification.repository.AuditLogRepository;
import com.fintech.wallet.modules.notification.service.EmailService;
import com.fintech.wallet.modules.notification.service.EmailTemplateBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final AuditLogRepository auditLogRepository;
    private final EmailService emailService;
    private final EmailTemplateBuilder emailTemplateBuilder;
    private final ObjectMapper objectMapper;

    // =======================================================
    // 1. CONSUMER CHO GIAO DỊCH (Lắng nghe: transaction-events)
    // =======================================================
    @KafkaListener(topics = "transaction-events", groupId = "notification-group")
    public void consumeTransactionEvent(String payload) {
        log.info("[KAFKA CONSUMER] Nhận sự kiện Giao Dịch: {}", payload);

        // 1. Lưu Nhật ký Audit Log bất biến
        auditLogRepository.save(AuditLog.builder()
                .topic("transaction-events")
                .payload(payload)
                .build());

        // 2. Parse payload và dựng Email Template ngân hàng
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            String accountId = jsonNode.has("accountId") ? jsonNode.get("accountId").asText() : "N/A";
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "TRANSACTION";
            double amount = jsonNode.has("amount") ? jsonNode.get("amount").asDouble() : 0.0;
            String recipientEmail = jsonNode.has("email") ? jsonNode.get("email").asText() : "quoctinhstudy1911@gmail.com";

            String subject = "[Fintech Wallet] Thông báo biến động số dư " + type;
            String htmlContent = emailTemplateBuilder.buildTransactionReceipt(accountId, type, amount);

            emailService.sendTransactionEmail(recipientEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi xử lý sự kiện Giao Dịch: ", e);
        }
    }

    // =======================================================
    // 2. CONSUMER CHO AUTH (Lắng nghe: notification-events)
    // =======================================================
    @KafkaListener(topics = "notification-events", groupId = "notification-group")
    public void consumeAuthEvent(String payload) {
        log.info("[KAFKA CONSUMER] Nhận sự kiện Đăng ký User: {}", payload);

        // 1. Lưu Nhật ký Audit Log bất biến
        auditLogRepository.save(AuditLog.builder()
                .topic("notification-events")
                .payload(payload)
                .build());

        // 2. Parse payload và gửi Email chào mừng
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            String fullName = jsonNode.has("fullName") ? jsonNode.get("fullName").asText() : "Quý khách";
            String phoneNumber = jsonNode.has("phoneNumber") ? jsonNode.get("phoneNumber").asText() : "N/A";
            String recipientEmail = jsonNode.has("email") ? jsonNode.get("email").asText() : "quoctinhstudy1911@gmail.com";

            String subject = "[Fintech Wallet] Chào mừng quý khách kích hoạt ví thành công!";
            String htmlContent = emailTemplateBuilder.buildWelcomeEmail(fullName, phoneNumber);

            emailService.sendTransactionEmail(recipientEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi xử lý sự kiện Auth Notification: ", e);
        }
    }
}