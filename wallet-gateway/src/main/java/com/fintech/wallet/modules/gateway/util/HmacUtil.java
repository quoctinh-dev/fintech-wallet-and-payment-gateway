package com.fintech.wallet.modules.gateway.util;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class HmacUtil {

    private static final String SECRET_KEY = "FintechWalletSuperSecretKeyForHmacSecurity";
    private static final String HMAC_ALGO = "HmacSHA256";

    /**
     * Hàm tính toán chữ ký điện tử HMAC-SHA256
     * @param payload Chuỗi dữ liệu cần bảo vệ (VD: method + path + timestamp + body)
     * @return Chuỗi Hex băm được
     */
    public String calculateHmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tính toán HMAC: " + e.getMessage());
        }
    }

    /**
     * Chuyển mảng Byte thành chuỗi Hex (String) để dễ so sánh
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
