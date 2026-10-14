package com.fintech.wallet.common.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VNPayUtils {

    private static final String HMAC_SHA512 = "HmacSHA512";

    /**
     * Băm danh sách tham số vnp_* thành chuỗi HashData chuẩn mã hóa VNPay
     */
    public static String hashAllFields(Map<String, String> fields, String secretKey) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.trim().isEmpty()
                    && !"vnp_SecureHash".equals(fieldName)
                    && !"vnp_SecureHashType".equals(fieldName)) {

                if (hashData.length() > 0) {
                    hashData.append("&");
                }
                hashData.append(fieldName);
                hashData.append("=");
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
            }
        }
        return hmacSHA512(secretKey, hashData.toString());
    }

    /**
     * Thuật toán băm HMAC-SHA512 chuẩn (Dùng cho VNPay 2.1.0)
     */
    public static String hmacSHA512(String key, String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key và Data không được để null");
            }
            Mac hmacSha512 = Mac.getInstance(HMAC_SHA512);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA512);
            hmacSha512.init(secretKey);

            byte[] result = hmacSha512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("[VNPayUtils] Lỗi khi tạo chữ ký HMAC-SHA512: {}", ex.getMessage(), ex);
            return "";
        }
    }

    /**
     * Trích xuất chính xác IP Client, hỗ trợ chạy sau Reverse Proxy / Cloud
     */
    public static String getIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return "0:0:0:0:0:0:0:1".equals(ipAddress) ? "127.0.0.1" : ipAddress;
    }
}