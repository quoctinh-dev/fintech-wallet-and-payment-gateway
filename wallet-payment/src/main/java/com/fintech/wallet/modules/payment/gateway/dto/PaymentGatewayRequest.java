package com.fintech.wallet.modules.payment.gateway.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO truyền vào PaymentGateway — provider-agnostic.
 * Không chứa bất kỳ field nào của VNPay, MoMo, ZaloPay riêng lẻ.
 */
@Getter
@Builder
public class PaymentGatewayRequest {

    /** Idempotency key dùng làm vnp_TxnRef / orderId / ... tuỳ provider */
    private final String idempotencyKey;

    /** Số tiền gốc (VND), gateway tự nhân 100 nếu cần */
    private final BigDecimal amount;

    /** Mô tả giao dịch */
    private final String description;

    /** ID tài khoản ví người dùng — dùng để ghi vào OrderInfo nếu cần */
    private final UUID accountId;

    /** IP address của người dùng */
    private final String clientIp;
}
