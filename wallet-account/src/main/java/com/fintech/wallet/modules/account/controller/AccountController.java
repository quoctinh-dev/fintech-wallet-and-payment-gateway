package com.fintech.wallet.modules.account.controller;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.common.dto.PaginationResponse;
import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.common.exception.ErrorCode;
import com.fintech.wallet.modules.account.dto.request.CreateAccountRequest;
import com.fintech.wallet.modules.account.dto.request.UpdateAccountStatusRequest;
import com.fintech.wallet.modules.account.dto.response.AccountResponse;
import com.fintech.wallet.modules.account.dto.response.AccountSearchResponse;
import com.fintech.wallet.modules.account.enums.AccountStatus;
import com.fintech.wallet.modules.account.enums.AccountType;
import com.fintech.wallet.modules.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(
        name = "03. Account & Wallet Management API (Quản lý Tài khoản & Ví)",
        description = "Các API Tra cứu người nhận, Xem danh sách ví, Chi tiết ví, Đóng băng ví, Đóng ví xóa mềm và Mở ví thủ công"
)
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/search")
    @Operation(
            summary = "14. Tra cứu thông tin người nhận theo số điện thoại",
            description = "Dành cho ROLE_USER hoặc ROLE_ADMIN trước khi chuyển tiền. Không lộ số dư, chặn tra cứu chính mình"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<AccountSearchResponse> searchRecipient(
            @RequestParam("phoneNumber") String phoneNumber,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String callerId,
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authHeader) {

        return ApiResponse.success(accountService.searchRecipientByPhone(phoneNumber, callerId, authHeader));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @GetMapping
    @Operation(
            summary = "15. Xem danh sách toàn bộ ví hệ thống",
            description = "Chỉ dành cho Quản trị viên (ROLE_ADMIN) và Kiểm toán viên (ROLE_AUDITOR). Hỗ trợ phân trang và lọc theo trạng thái,loại ví"
            )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<PaginationResponse<AccountResponse>> getAccounts(
            @RequestParam(value = "status", required = false) AccountStatus status,
            @RequestParam(value = "type", required = false) AccountType accountType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        return ApiResponse.success(accountService.getAccounts(status, accountType, page, size));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "16. Xem chi tiết thông tin ví theo Account ID",
            description = "Quản trị viên, Kiểm toán viên hoặc Chính chủ sở hữu ví (kiểm soát ABAC theo X-User-Id và X-User-Roles)"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<AccountResponse> getAccountById(
            @PathVariable("id") UUID accountId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String callerId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_USER") String role) {

        AccountResponse response = accountService.getAccountById(accountId);

        boolean isPrivileged = role.contains("ADMIN") || role.contains("AUDITOR");
        if (!isPrivileged && !response.getUserId().equals(UUID.fromString(callerId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return ApiResponse.success(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(
            summary = "17. Xem ví theo User ID và Loại ví",
            description = "Quản trị viên hoặc Chính chủ tài khoản (kiểm soát ABAC theo X-User-Id và X-User-Roles)"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<AccountResponse> getAccountByUserIdAndType(
            @PathVariable("userId") UUID userId,
            @RequestParam(name = "type", defaultValue = "USER_WALLET") AccountType accountType,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String callerId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_USER") String role) {

        boolean isAdmin = role.contains("ADMIN");
        if (!isAdmin && !userId.equals(UUID.fromString(callerId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return ApiResponse.success(accountService.getAccountByUserIdAndType(userId, accountType));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/status")
    @Operation(
            summary = "18. Đóng băng / Mở khóa ví nghi vấn gian lận",
            description = "Chỉ dành cho Quản trị viên (ROLE_ADMIN). Cho phép chuyển trạng thái giữa ACTIVE và FROZEN"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<AccountResponse> updateAccountStatus(
            @PathVariable("id") UUID accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {

        return ApiResponse.success("Cập nhật trạng thái ví thành công", accountService.updateAccountStatus(accountId, request));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "19. Xóa mềm / Đóng ví vĩnh viễn (Soft Delete Account)",
            description = "Quản trị viên hoặc Chính chủ sở hữu ví. Ràng buộc: số dư phải bằng 0 (balance == 0.00)"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<String> closeAccount(
            @PathVariable("id") UUID accountId,
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String callerId,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Roles", defaultValue = "ROLE_USER") String role) {

        AccountResponse account = accountService.getAccountById(accountId);

        boolean isAdmin = role.contains("ADMIN");
        if (!isAdmin && !account.getUserId().equals(UUID.fromString(callerId))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        accountService.closeAccount(accountId);
        return ApiResponse.success("Đóng tài khoản ví thành công!");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "20. Tạo tài khoản ví thủ công",
            description = "Chỉ dành cho Quản trị viên (ROLE_ADMIN) để mở ví hệ thống hoặc ví người dùng đặc biệt"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ApiResponse.success("Tạo tài khoản ví thành công", accountService.createAccount(request));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Xem thông tin ví cá nhân của chính mình",
            description = "Tự động nhận diện người dùng từ Access Token (X-User-Id). Không cần nhập bất kỳ ID hay tham số nào"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<AccountResponse> getMyWallet(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") String callerId,
            @RequestParam(name = "type", defaultValue = "USER_WALLET") AccountType accountType) {

        return ApiResponse.success(accountService.getAccountByUserIdAndType(UUID.fromString(callerId), accountType));
    }

}
