package com.fintech.wallet.modules.auth.repository;

import com.fintech.wallet.modules.auth.entity.User;
import com.fintech.wallet.modules.auth.enums.KycStatus;
import com.fintech.wallet.modules.auth.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByCitizenId(String citizenId);

    boolean existsByCitizenIdAndIdNot(String citizenId, UUID id);

    // Mặc định ẩn User DELETED trừ khi Admin cố tình lọc theo ?status=DELETED
    @Query("SELECT u FROM User u WHERE " +
            "(:role IS NULL OR u.roles = :role) AND " +
            "(:kycStatus IS NULL OR u.kycStatus = :kycStatus) AND " +
            "((:status IS NOT NULL AND u.status = :status) OR (:status IS NULL AND u.status <> com.fintech.wallet.modules.auth.enums.UserStatus.DELETED)) AND " +
            "(:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', CAST(:keyword AS String), '%')) OR u.phoneNumber LIKE CONCAT('%', CAST(:keyword AS String), '%'))")
    Page<User> searchUsers(
            @Param("role") String role,
            @Param("kycStatus") KycStatus kycStatus,
            @Param("status") UserStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}