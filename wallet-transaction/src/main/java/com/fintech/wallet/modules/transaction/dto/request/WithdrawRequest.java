package com.fintech.wallet.modules.transaction.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class WithdrawRequest {
    @NotNull(message = "AccountId không được để trống")
    private UUID accountId;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "10000.00", message = "Số tiền rút tối thiểu là 10,000 VND")
    private BigDecimal amount;

    @NotBlank(message = "Mã ngân hàng không được để trống")
    private String bankCode;

    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    private String bankAccountNumber;

    @NotBlank(message = "Idempotency key không được để trống")
    private String idempotencyKey;

    private String description;
}
