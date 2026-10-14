package com.fintech.wallet.modules.notification.service;

import com.fintech.wallet.modules.notification.config.BrevoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final BrevoConfig brevoConfig;
    private final RestTemplate restTemplate;

    public void sendTransactionEmail(String to, String subject, String htmlContent) {
        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoConfig.getApiKey());

        Map<String, Object> body = Map.of(
                "sender", Map.of(
                        "name", brevoConfig.getSenderName(),
                        "email", brevoConfig.getSenderEmail()
                ),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", htmlContent
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Gửi email thành công tới {}. Phản hồi từ hệ thống: {}", to, response.getBody());
            } else {
                log.warn("Brevo trả về mã trạng thái không thành công: {} khi gửi tới {}", response.getStatusCode(), to);
            }
        } catch (HttpStatusCodeException e) {
            log.error("Lỗi gửi email tới {} qua Brevo API. Mã trạng thái: {}, Nội dung lỗi: {}",
                    to, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Lỗi không xác định khi gửi email tới {}: ", to, e);
        }
    }
}