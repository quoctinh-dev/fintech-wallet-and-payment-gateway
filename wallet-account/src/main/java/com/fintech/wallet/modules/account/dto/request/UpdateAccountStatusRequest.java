package com.fintech.wallet.modules.account.dto.request;

import com.fintech.wallet.modules.account.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAccountStatusRequest {

    @NotNull(message = "Trạng thái tài khoản ví không được để trống")
    private AccountStatus status;

    private String reason;
}
