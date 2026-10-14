package com.fintech.wallet.modules.transaction.dto.request;

import com.fintech.wallet.common.enums.PaymentProvider;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DepositRequest {

    @NotNull(message = "accountId không được để trống")
    private UUID accountId;

    @NotNull(message = "Số tiền nạp không được để trống")
    @DecimalMin(value = "10000.00", message = "Số tiền nạp tối thiểu là 10,000 VND")
    @Digits(integer = 15, fraction = 2, message = "Số tiền không đúng định dạng quy chuẩn")
    private BigDecimal amount;

    @NotBlank(message = "idempotencyKey không được để trống")
    private String idempotencyKey;

    private String description;

    /** Cổng thanh toán sử dụng. Mặc định VNPAY nếu không truyền. */
    @NotNull(message = "provider không được để trống")
    private PaymentProvider provider = PaymentProvider.VNPAY;
}