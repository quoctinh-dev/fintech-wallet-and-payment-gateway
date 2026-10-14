package com.fintech.wallet.modules.account.repository;

import com.fintech.wallet.modules.account.entity.Account;
import com.fintech.wallet.modules.account.enums.AccountStatus;
import com.fintech.wallet.modules.account.enums.AccountType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserIdAndAccountType(UUID userId, AccountType accountType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findWithLockById(@Param("id") UUID id);

    @Query("SELECT a FROM Account a WHERE " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:accountType IS NULL OR a.accountType = :accountType)")
    Page<Account> searchAccounts(
            @Param("status") AccountStatus status,
            @Param("accountType") AccountType accountType,
            Pageable pageable
    );
}