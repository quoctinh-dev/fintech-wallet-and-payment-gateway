
package com.fintech.wallet.modules.transaction.controller;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.modules.transaction.dto.request.TransferRequest;
import com.fintech.wallet.modules.transaction.dto.response.TransferResponse;
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
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(
        name = "04. Transaction Engine API (Xử lý Giao dịch & Tra cứu)",
        description = "Chuyển tiền P2P giữa hai ví cá nhân"
)
public class TransferController {

    private final TransactionService transactionService;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping
    @Operation(
            summary = "21. Chuyển tiền P2P giữa hai ví người dùng",
            description = "Bắt buộc có chữ ký điện tử X-Signature (HMAC-SHA256) và X-Idempotency-Key. Cơ chế khóa hai chiều chống Deadlock"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ApiResponse.success("Chuyển khoản thành công", transactionService.transfer(request));
    }
}
