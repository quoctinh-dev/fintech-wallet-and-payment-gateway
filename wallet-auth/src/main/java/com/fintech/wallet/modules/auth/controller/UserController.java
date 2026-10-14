package com.fintech.wallet.modules.auth.controller;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.common.dto.PaginationResponse;
import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.common.exception.ErrorCode;
import com.fintech.wallet.modules.auth.dto.request.ReviewKycRequest;
import com.fintech.wallet.modules.auth.dto.request.UpdateUserRoleRequest;
import com.fintech.wallet.modules.auth.dto.request.UpdateUserStatusRequest;
import com.fintech.wallet.modules.auth.dto.response.UserResponse;
import com.fintech.wallet.modules.auth.enums.KycStatus;
import com.fintech.wallet.modules.auth.enums.UserStatus;
import com.fintech.wallet.modules.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(
        name = "02. User Management API (Quản trị & Phân quyền)",
        description = "Các API Quản trị danh sách người dùng, Tra cứu chi tiết, Phân quyền Role, Khóa tài khoản, Duyệt eKYC và Xóa mềm"
)
public class UserController {

    private final UserService userService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    @Operation(
            summary = "08. Xem danh sách toàn bộ người dùng",
            description = "Chỉ dành cho Quản trị viên (ROLE_ADMIN). Hỗ trợ phân trang và lọc theo role, kycStatus, status, keyword"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<PaginationResponse<UserResponse>> getUsers(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "kycStatus", required = false) KycStatus kycStatus,
            @RequestParam(value = "status", required = false) UserStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        return ApiResponse.success(userService.getUsers(role, kycStatus, status, keyword, page, size));
    }

    @GetMapping("/{userId}")
    @Operation(
            summary = "09. Xem chi tiết thông tin một người dùng",
            description = "Quản trị viên (ROLE_ADMIN) hoặc Chính chủ tài khoản (kiểm soát ABAC theo X-User-Id và X-User-Roles)"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<UserResponse> getUserById(
            @PathVariable("userId") UUID userId,
            @RequestHeader("X-User-Id") String callerId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_USER") String role) {

        boolean isAdmin = role.contains("ADMIN");
        if (!isAdmin && !userId.equals(UUID.fromString(callerId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return ApiResponse.success(userService.getUserById(userId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/status")
    @Operation(
            summary = "10. Khóa / Mở khóa tài khoản người dùng",
            description = "Chỉ dành cho Quản trị viên (ROLE_ADMIN). Có cơ chế chặn Admin tự khóa chính mình hoặc khóa Admin khác"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<UserResponse> updateUserStatus(
            @PathVariable("userId") UUID userId,
            @RequestHeader("X-User-Id") String currentAdminId,
            @Valid @RequestBody UpdateUserStatusRequest request) {

        return ApiResponse.success(userService.updateUserStatus(userId, UUID.fromString(currentAdminId), request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/role")
    @Operation(
            summary = "11. Cập nhật vai trò người dùng (Role Management)",
            description = "Chỉ dành cho Quản trị viên (ROLE_ADMIN). Có cơ chế chặn Admin tự hạ quyền hoặc thay đổi quyền Admin khác"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<UserResponse> updateUserRole(
            @PathVariable("userId") UUID userId,
            @RequestHeader("X-User-Id") String currentAdminId,
            @Valid @RequestBody UpdateUserRoleRequest request) {

        return ApiResponse.success(userService.updateUserRole(userId, UUID.fromString(currentAdminId), request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/kyc")
    @Operation(
            summary = "12. Thẩm định & Phê duyệt/Từ chối eKYC",
            description = "Chỉ dành cho Quản trị viên (ROLE_ADMIN). Phê duyệt VERIFIED hoặc REJECTED kèm lý do từ chối"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<UserResponse> reviewKyc(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody ReviewKycRequest request) {

        return ApiResponse.success(userService.reviewKyc(userId, request));
    }

    @DeleteMapping("/{userId}")
    @Operation(
            summary = "13. Xóa mềm tài khoản người dùng (Soft Delete)",
            description = "Quản trị viên hoặc Chính chủ yêu cầu đóng tài khoản vĩnh viễn (kiểm soát ABAC theo X-User-Id và X-User-Roles)"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<String> deleteUser(
            @PathVariable("userId") UUID userId,
            @RequestHeader("X-User-Id") String callerId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_USER") String role) {

        boolean isAdmin = role.contains("ADMIN");
        if (!isAdmin && !userId.equals(UUID.fromString(callerId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        userService.softDeleteUser(userId, UUID.fromString(callerId), isAdmin);
        return ApiResponse.success("Đã xóa mềm và đóng tài khoản thành công!");
    }

    @GetMapping("/recipient")
    @Operation(
            summary = "Tra cứu thông tin người nhận theo số điện thoại",
            description = "Phục vụ tìm kiếm tên người nhận trước khi chuyển tiền"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<UserResponse> getRecipientByPhone(@RequestParam("phoneNumber") String phoneNumber) {
        return ApiResponse.success(userService.getRecipientByPhone(phoneNumber));
    }

}