package com.fintech.wallet.modules.ledger.repository;

import com.fintech.wallet.modules.ledger.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Tính tổng số dư của tài khoản theo thời gian thực.
     *
     * Sử dụng COALESCE để đảm bảo trả về 0
     * nếu tài khoản chưa có lịch sử Ledger.
     */
    @Query("""
        SELECT COALESCE(SUM(l.amount), 0)
        FROM LedgerEntry l
        WHERE l.accountId = :accountId
        """)
    BigDecimal getBalanceByAccountId(@Param("accountId") UUID accountId);

    /**
     * Truy vấn danh sách bút toán theo mã giao dịch.
     * Dùng cho Unit Test / Integration Test.
     */
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /**
     * Truy vấn toàn bộ lịch sử bút toán của một tài khoản.
     * Dùng cho Audit / Test.
     */
    List<LedgerEntry> findByAccountId(UUID accountId);

    /**
     * Truy vấn lịch sử bút toán của tài khoản có phân trang.
     */
    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);

    /**
     * Tính tổng toàn bộ biến động trong Sổ cái.
     * Theo Kế toán kép: Tổng Debit + Credit phải bằng 0.
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l")
    BigDecimal getTotalLedgerBalance();

    /**
     * Đếm tổng số bút toán đã ghi nhận.
     */
    @Query("SELECT COUNT(l) FROM LedgerEntry l")
    long getTotalEntriesCount();

}
