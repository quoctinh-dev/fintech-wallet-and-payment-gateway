package com.fintech.wallet.modules.transaction.repository;

import com.fintech.wallet.modules.transaction.entity.Transaction;
import com.fintech.wallet.modules.transaction.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT t
            FROM Transaction t
            WHERE t.id IN :transactionIds
            """)
    List<Transaction> findTransactionsByIds(
            @Param("transactionIds") List<UUID> transactionIds
    );

    @Query("""
            SELECT t
            FROM Transaction t
            WHERE (:status IS NULL OR t.status = :status)
            """)
    Page<Transaction> searchTransactions(
            @Param("status") TransactionStatus status,
            Pageable pageable
    );
}