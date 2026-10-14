package com.fintech.wallet.modules.transaction.controller;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.common.dto.PaginationResponse;
import com.fintech.wallet.modules.transaction.dto.response.TransactionHistoryResponse;
import com.fintech.wallet.modules.transaction.dto.response.TransactionResponse;
import com.fintech.wallet.modules.transaction.enums.TransactionStatus;
import com.fintech.wallet.modules.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(
        name = "04. Transaction Engine API (Xử lý Giao dịch & Tra cứu)",
        description = "Các API Tra cứu lịch sử ví cá nhân, Quản trị danh sách toàn bộ giao dịch sàn và Xem chi tiết 1 giao dịch"
)
public class TransactionController {

    private final TransactionService transactionService;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @GetMapping("/history/{accountId}")
    @Operation(
            summary = "23. Tra cứu lịch sử giao dịch của một tài khoản ví",
            description = "Quản trị viên, Kiểm toán viên hoặc Chính chủ sở hữu ví (kiểm soát ABAC theo X-User-Id). Phân trang kết quả"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<PaginationResponse<TransactionHistoryResponse>> getHistory(
            @PathVariable("accountId") UUID accountId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TransactionHistoryResponse> historyPage = transactionService.getTransactionHistory(accountId, pageable);
        return ApiResponse.success(PaginationResponse.fromPage(historyPage));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @GetMapping
    @Operation(
            summary = "24. Quản trị tra cứu toàn bộ giao dịch hệ thống",
            description = "Chỉ dành cho Quản trị viên (ROLE_ADMIN) và Kiểm toán viên (ROLE_AUDITOR). Hỗ trợ phân trang và lọc theo trạng tháigiao dịch"
            )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<PaginationResponse<TransactionResponse>> getAllTransactions(
            @RequestParam(value = "status", required = false) TransactionStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TransactionResponse> transactionPage = transactionService.getAllTransactions(status, pageable);
        return ApiResponse.success(PaginationResponse.fromPage(transactionPage));
    }

    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @GetMapping("/{id}")
    @Operation(
            summary = "25. Tra cứu chi tiết một giao dịch tài chính",
            description = "Quản trị viên, Kiểm toán viên hoặc các bên liên quan đến giao dịch"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ApiResponse<TransactionResponse> getTransactionById(@PathVariable("id") UUID transactionId) {
        return ApiResponse.success(transactionService.getTransactionById(transactionId));
    }
}
