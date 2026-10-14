package com.fintech.wallet.modules.auth.dto.request;

import com.fintech.wallet.modules.auth.enums.KycStatus;
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
public class ReviewKycRequest {

    @NotNull(message = "Trạng thái phê duyệt không được để trống")
    private KycStatus status;

    private String rejectionReason;
}
