package com.fintech.wallet.modules.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResponse {
    private boolean balanced;          // true nếu tổng Debit + Credit = 0
    private BigDecimal totalDelta;     // Độ lệch (Kỳ vọng: 0.00)
    private long totalEntries;         // Tổng số bút toán trong hệ thống
    private String status;             // BALANCED_ZERO_ERROR / UNBALANCED_CRITICAL_ERROR
    private LocalDateTime checkedAt;
}
