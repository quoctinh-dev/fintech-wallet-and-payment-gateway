package com.fintech.wallet.modules.transaction.controller;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.modules.transaction.dto.request.WithdrawRequest;
import com.fintech.wallet.modules.transaction.dto.response.WithdrawResponse;
import com.fintech.wallet.modules.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
@Tag(
        name = "04. Transaction Engine API (Xử lý Giao dịch & Tra cứu)",
        description = "Rút tiền từ ví người dùng về tài khoản ngân hàng"
)
public class WithdrawController {

    private final TransactionService transactionService;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping
    @Operation(
            summary = "22. Rút tiền từ ví về tài khoản ngân hàng",
            description = "Bắt buộc có chữ ký điện tử X-Signature (HMAC-SHA256) và X-Idempotency-Key. Ghi nợ ví người dùng và đối ứng ví Bank Clearing"
            )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<WithdrawResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        return ApiResponse.success("Rút tiền thành công", transactionService.withdraw(request));
    }
}
