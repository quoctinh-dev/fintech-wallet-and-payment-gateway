package com.fintech.wallet.modules.account.dto.request;

import com.fintech.wallet.modules.account.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAccountRequest {

    @NotNull(message = "userId không được để trống")
    private UUID userId;

    @NotNull(message = "accountType không được để trống")
    private AccountType accountType;

    @NotBlank(message = "currency không được để trống")
    @Pattern(regexp = "^(VND|USD)$", message = "Tiền tệ chỉ hỗ trợ VND hoặc USD")
    private String currency;
}