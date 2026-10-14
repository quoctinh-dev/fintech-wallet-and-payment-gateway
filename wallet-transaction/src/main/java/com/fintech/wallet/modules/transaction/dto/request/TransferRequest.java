package com.fintech.wallet.modules.transaction.dto.request;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferRequest {
    @NotNull(message = "fromAccountId không được để trống")
    private UUID fromAccountId;

    @NotNull(message = "toAccountId không được để trống")
    private UUID toAccountId;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "1000.00", message = "Số tiền chuyển tối thiểu là 1,000 VND")
    @Digits(integer = 15, fraction = 2, message = "Số tiền không đúng định dạng")
    private BigDecimal amount;

    @NotBlank(message = "idempotencyKey không được để trống")
    private String idempotencyKey;

    private String description;

}
