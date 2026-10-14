package com.fintech.wallet.modules.ledger.controller;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.common.dto.PaginationResponse;
import com.fintech.wallet.modules.ledger.dto.LedgerEntryResponse;
import com.fintech.wallet.modules.ledger.dto.ReconciliationResponse;
import com.fintech.wallet.modules.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(
        name = "06. Financial Audit & Ledger API (Kiểm toán & Sổ cái)",
        description = "Báo cáo đối soát sổ cái kế toán kép cân bằng tổng 0 và tra cứu lịch sử bút toán"
)
public class LedgerController {

    private final LedgerService ledgerService;

    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @GetMapping("/reconciliation")
    @Operation(
            summary = "29. Báo cáo Đối soát Sổ cái Kế toán kép (Financial Reconciliation)",
            description = "Kiểm tra tính cân bằng của toàn bộ sổ cái kế toán kép. Kỳ vọng: Tổng độ lệch (Total Delta) = 0.00"
    )
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> reconcile() {
        ReconciliationResponse result = ledgerService.reconcileLedger();
        return ResponseEntity.ok(ApiResponse.success("Đối soát sổ cái thành công", result));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @GetMapping("/entries")
    @Operation(
            summary = "30. Tra cứu danh sách bút toán sổ cái (Ledger Entries Audit)",
            description = "Dành cho Quản trị viên và Kiểm toán viên tra cứu chi tiết từng bút toán Nợ/Có. Hỗ trợ lọc theoaccountId và phân trang"
            )
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ApiResponse<PaginationResponse<LedgerEntryResponse>>> getLedgerEntries(
            @Parameter(description = "Lọc theo ID tài khoản ví (tùy chọn)")
            @RequestParam(value = "accountId", required = false) UUID accountId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(PaginationResponse.fromPage(ledgerService.getLedgerEntries(accountId,
                pageable))));
    }
}
