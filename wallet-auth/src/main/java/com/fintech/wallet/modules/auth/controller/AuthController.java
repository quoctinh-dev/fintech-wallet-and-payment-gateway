package com.fintech.wallet.modules.auth.controller;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.modules.auth.dto.request.ChangePasswordRequest;
import com.fintech.wallet.modules.auth.dto.request.KycSubmitRequest;
import com.fintech.wallet.modules.auth.dto.request.LoginRequest;
import com.fintech.wallet.modules.auth.dto.request.RefreshTokenRequest;
import com.fintech.wallet.modules.auth.dto.request.RegisterRequest;
import com.fintech.wallet.modules.auth.dto.response.AuthResponse;
import com.fintech.wallet.modules.auth.dto.response.UserResponse;
import com.fintech.wallet.modules.auth.service.AuthService;
import com.fintech.wallet.modules.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(
        name = "01. Authentication & Profile API",
        description = "Các API Đăng ký, Đăng nhập, Token Rotation, Đăng xuất, Hồ sơ cá nhân, Đổi mật khẩu và Nộp eKYC"
)
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(
            summary = "01. Đăng ký tài khoản người dùng mới",
            description = "Tạo tài khoản ví mới, sinh ví mặc định USER_WALLET và phát sự kiện chào mừng qua Kafka Outbox"
    )
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "02. Đăng nhập hệ thống",
            description = "Xác thực SĐT và Mật khẩu, trả về Access Token (chứa Roles) và Refresh Token. Chặn nếu tài khoản bị BLOCKED hoặc DELETED"
    )
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "03. Cấp lại Token (Token Refresh)",
            description = "Sử dụng Refresh Token còn hạn để cấp cặp Token mới (áp dụng kỹ thuật Token Rotation hủy vé cũ)"
    )
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request));
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @PostMapping("/logout")
    @Operation(
            summary = "04. Đăng xuất hệ thống",
            description = "Thu hồi Access Token đưa vào Redis Blacklist và xóa sạch Refresh Token trong CSDL"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<String> logout(
            @RequestHeader("Authorization") String bearerToken,
            @RequestHeader("X-User-Id") String userId) {

        String accessToken = bearerToken.substring(7);
        authService.logout(accessToken, userId);
        return ApiResponse.success("Đăng xuất thành công, Token đã bị thu hồi!");
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @GetMapping("/me")
    @Operation(
            summary = "05. Xem thông tin cá nhân của chính mình",
            description = "Lấy thông tin hồ sơ của người dùng đang đăng nhập dựa trên ID do Gateway bóc tách chuyển tiếp"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<UserResponse> getProfile(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(userService.getProfile(UUID.fromString(userId)));
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @PutMapping("/password")
    @Operation(
            summary = "06. Đổi mật khẩu tài khoản",
            description = "Khách hàng đổi mật khẩu của chính mình. Bắt buộc xác thực mật khẩu cũ bằng BCrypt và chặn trùng mật khẩu cũ"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<String> changePassword(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(UUID.fromString(userId), request);
        return ApiResponse.success("Đổi mật khẩu thành công!");
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/kyc/submit")
    @Operation(
            summary = "07. Nộp hồ sơ định danh eKYC",
            description = "Chỉ dành cho ROLE_USER. Gửi số CCCD (12 số) và link ảnh mặt trước/sau để nâng cấp tài khoản lên trạng thái PENDING"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<UserResponse> submitKyc(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody KycSubmitRequest request) {

        return ApiResponse.success(userService.submitKyc(UUID.fromString(userId), request));
    }
}