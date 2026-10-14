package com.fintech.wallet.modules.payment.gateway;

import com.fintech.wallet.common.enums.PaymentProvider;
import com.fintech.wallet.modules.payment.gateway.dto.PaymentCallbackResult;
import com.fintech.wallet.modules.payment.gateway.dto.PaymentGatewayRequest;

import java.util.Map;

/**
 * Contract chung cho mọi cổng thanh toán.
 *
 * <p>Để tích hợp provider mới (MoMo, ZaloPay, Stripe...):
 * <ol>
 *   <li>Implement interface này</li>
 *   <li>Đánh {@code @Service} để Spring tự đăng ký vào gateway registry</li>
 *   <li>Thêm provider vào enum {@link PaymentProvider}</li>
 * </ol>
 */
public interface PaymentGateway {

    /**
     * Trả về provider mà implementation này phụ trách.
     * Dùng để TransactionService chọn đúng gateway.
     */
    PaymentProvider getProvider();

    /**
     * Tạo URL redirect để người dùng thanh toán trên trang của provider.
     *
     * @param request thông tin giao dịch (provider-agnostic)
     * @return URL redirect đầy đủ bao gồm chữ ký
     */
    String createPaymentUrl(PaymentGatewayRequest request);

    /**
     * Xử lý IPN (Instant Payment Notification) / Webhook từ provider.
     * Method này chỉ làm nhiệm vụ:
     * <ul>
     *   <li>Xác minh chữ ký</li>
     *   <li>Parse kết quả thanh toán</li>
     *   <li>Trả về {@link PaymentCallbackResult} để TransactionService xử lý DB</li>
     * </ul>
     * Không được ghi DB trong method này.
     *
     * @param params query params / body từ provider gửi về
     * @return kết quả parse từ callback
     */
    PaymentCallbackResult processIPN(Map<String, String> params);

    /**
     * Xác minh chữ ký trên Return URL (browser redirect sau thanh toán).
     *
     * @param params query params trên Return URL
     * @return true nếu chữ ký hợp lệ
     */
    boolean verifyReturn(Map<String, String> params);
}
