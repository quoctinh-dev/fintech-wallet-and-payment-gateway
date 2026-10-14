package com.fintech.wallet.modules.payment.dto;

import com.fintech.wallet.common.enums.PaymentProvider;
import com.fintech.wallet.common.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentDepositRequest {

    @Schema(
            description = "ID ví cần nạp. Nếu để trống (null), hệ thống TỰ ĐỘNG nạp vào ví USER_WALLET mặc định của chính bạn",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private UUID accountId;

    @NotNull(message = "amount không được để trống")
    @DecimalMin(value = "10000.0", message = "Số tiền nạp tối thiểu là 10.000 VNĐ")
    @Schema(description = "Số tiền nạp (Tối thiểu 10,000 VND)", example = "50000.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @NotBlank(message = "idempotencyKey không được để trống")
    @Schema(description = "Khóa chống trùng lặp giao dịch", example = "vnpay-topup-001", requiredMode = Schema.RequiredMode.
            REQUIRED)
    private String idempotencyKey;

    @Schema(description = "Mô tả nội dung nạp tiền", example = "Nap tien vi qua VNPay")
    private String description;

    @NotNull(message = "provider không được để trống")
    @Schema(description = "Cổng thanh toán", example = "VNPAY", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaymentProvider provider;
}
