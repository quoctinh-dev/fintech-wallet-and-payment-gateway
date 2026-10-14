package com.fintech.wallet.modules.auth.dto.request;

import com.fintech.wallet.modules.auth.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserStatusRequest {

    @NotNull(message = "Trạng thái tài khoản không được để trống")
    private UserStatus status;
}
