package com.fintech.wallet.modules.payment.gateway.impl;

import com.fintech.wallet.common.utils.VNPayUtils;
import com.fintech.wallet.config.VNPayConfig;
import com.fintech.wallet.common.enums.PaymentProvider;
import com.fintech.wallet.modules.payment.gateway.PaymentGateway;
import com.fintech.wallet.modules.payment.gateway.dto.PaymentCallbackResult;
import com.fintech.wallet.modules.payment.gateway.dto.PaymentGatewayRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Implementation của PaymentGateway cho VNPay.
 * Chứa toàn bộ logic đặc thù VNPay: build params, HMAC-SHA256, parse IPN.
 * TransactionService KHÔNG biết gì về VNPay — chỉ gọi qua interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayGatewayImpl implements PaymentGateway {

    private static final String DATE_FORMAT_PATTERN = "yyyyMMddHHmmss";
    private static final ZoneId VNPAY_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VNPayConfig vnPayConfig;

    // =========================================================================
    // PaymentGateway contract
    // =========================================================================

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public String createPaymentUrl(PaymentGatewayRequest request) {
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version",   vnPayConfig.getVersion());
        vnpParams.put("vnp_Command",   vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode",   vnPayConfig.getTmnCode());
        vnpParams.put("vnp_CurrCode",  "VND");
        vnpParams.put("vnp_TxnRef",    request.getIdempotencyKey());
        vnpParams.put("vnp_OrderInfo", "Nap tien vi: " + request.getAccountId());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale",    "vn");
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        vnpParams.put("vnp_IpAddr",    request.getClientIp());

        // Số tiền VNPay yêu cầu nhân 100 (không có dấu thập phân)
        long amountInCents = request.getAmount().multiply(new BigDecimal(100)).longValue();
        vnpParams.put("vnp_Amount", String.valueOf(amountInCents));

        // Thời gian tạo và hết hạn theo giờ Việt Nam
        ZonedDateTime now = ZonedDateTime.now(VNPAY_ZONE_ID);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN);
        vnpParams.put("vnp_CreateDate", now.format(formatter));

        int expireMinutes = vnPayConfig.getExpireMinutes() != null ? vnPayConfig.getExpireMinutes() : 15;
        vnpParams.put("vnp_ExpireDate", now.plusMinutes(expireMinutes).format(formatter));

        // Tính chữ ký HMAC-SHA256
        String secureHash = VNPayUtils.hashAllFields(vnpParams, vnPayConfig.getSecretKey());

        // Build query string
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder query = new StringBuilder();
        for (String field : fieldNames) {
            String value = vnpParams.get(field);
            if (value != null && !value.isEmpty()) {
                if (query.length() > 0) query.append('&');
                query.append(URLEncoder.encode(field, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
            }
        }
        query.append("&vnp_SecureHash=").append(secureHash);

        String paymentUrl = vnPayConfig.getPayUrl() + "?" + query;
        log.info("[VNPay] Đã tạo payment URL cho TxnRef: {}", request.getIdempotencyKey());
        return paymentUrl;
    }

    @Override
    public PaymentCallbackResult processIPN(Map<String, String> params) {
        // 1. Xác minh chữ ký HMAC-SHA256
        String receivedHash = params.get("vnp_SecureHash");
        String calculatedHash = VNPayUtils.hashAllFields(params, vnPayConfig.getSecretKey());

        if (!calculatedHash.equalsIgnoreCase(receivedHash)) {
            log.error("[VNPay IPN] Sai chữ ký! Calculated: {}, Received: {}", calculatedHash, receivedHash);
            return PaymentCallbackResult.builder()
                    .validSignature(false)
                    .paymentSuccess(false)
                    .ackCode("97")
                    .ackMessage("Invalid Checksum")
                    .build();
        }

        // 2. Đọc kết quả thanh toán từ VNPay
        String idempotencyKey = params.get("vnp_TxnRef");
        String responseCode   = params.get("vnp_ResponseCode");
        boolean isSuccess     = "00".equals(responseCode);

        log.info("[VNPay IPN] TxnRef={}, ResponseCode={}, Success={}", idempotencyKey, responseCode, isSuccess);

        return PaymentCallbackResult.builder()
                .validSignature(true)
                .paymentSuccess(isSuccess)
                .idempotencyKey(idempotencyKey)
                .responseCode(responseCode)
                .ackCode("00")
                .ackMessage("Confirm Success")
                .build();
    }

    @Override
    public boolean verifyReturn(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            log.warn("[VNPay Return] Query params rỗng.");
            return false;
        }
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isEmpty()) {
            log.warn("[VNPay Return] Thiếu vnp_SecureHash.");
            return false;
        }

        String calculatedHash = VNPayUtils.hashAllFields(params, vnPayConfig.getSecretKey());
        boolean valid = calculatedHash.equalsIgnoreCase(receivedHash);

        if (!valid) {
            log.error("[VNPay Return] Chữ ký không hợp lệ! TxnRef: {}", params.get("vnp_TxnRef"));
        } else {
            log.info("[VNPay Return] Chữ ký hợp lệ, TxnRef: {}", params.get("vnp_TxnRef"));
        }
        return valid;
    }
}
