package com.fintech.wallet.modules.payment.gateway.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Kết quả trả về từ PaymentGateway sau khi xử lý IPN/Callback.
 * TransactionService đọc object này để quyết định cập nhật DB.
 */
@Getter
@Builder
public class PaymentCallbackResult {

    /** Chữ ký checksum có hợp lệ không */
    private final boolean validSignature;

    /** VNPay/MoMo xác nhận thanh toán thành công không */
    private final boolean paymentSuccess;

    /** Idempotency key để TransactionService tra cứu Transaction trong DB */
    private final String idempotencyKey;

    /** Mã lỗi trả về từ provider (vnp_ResponseCode, resultCode, ...) */
    private final String responseCode;

    /** RspCode trả ngược lại cho gateway (VNPay yêu cầu "00" = đã nhận) */
    private final String ackCode;

    /** Message trả ngược lại cho gateway */
    private final String ackMessage;
}
