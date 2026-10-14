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
public class KycSubmitRequest {

    @NotBlank(message = "Số CCCD/Hộ chiếu không được để trống")
    @Pattern(regexp = "^[0-9]{12}$", message = "Số CCCD phải bao gồm đúng 12 chữ số")
    private String citizenId;

    @NotBlank(message = "Ảnh mặt trước CCCD không được để trống")
    private String idCardFrontUrl;

    @NotBlank(message = "Ảnh mặt sau CCCD không được để trống")
    private String idCardBackUrl;
}
