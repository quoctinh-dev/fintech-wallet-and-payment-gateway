
package com.fintech.wallet.modules.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class UpdateUserRoleRequest {

    @NotBlank(message = "Vai trò không được để trống")
    @Pattern(
            regexp = "^(ROLE_USER|ROLE_ADMIN|ROLE_AUDITOR)$",
            message = "Vai trò chỉ được là ROLE_USER, ROLE_ADMIN hoặc ROLE_AUDITOR"
    )
    private String role;
}
