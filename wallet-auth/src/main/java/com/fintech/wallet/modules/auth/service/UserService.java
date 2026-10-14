package com.fintech.wallet.modules.auth.service;

import com.fintech.wallet.common.dto.PaginationResponse;
import com.fintech.wallet.modules.auth.dto.request.ChangePasswordRequest;
import com.fintech.wallet.modules.auth.dto.request.KycSubmitRequest;
import com.fintech.wallet.modules.auth.dto.request.ReviewKycRequest;
import com.fintech.wallet.modules.auth.dto.request.UpdateUserRoleRequest;
import com.fintech.wallet.modules.auth.dto.request.UpdateUserStatusRequest;
import com.fintech.wallet.modules.auth.dto.response.UserResponse;
import com.fintech.wallet.modules.auth.enums.KycStatus;
import com.fintech.wallet.modules.auth.enums.UserStatus;

import java.util.UUID;

public interface UserService {

    UserResponse getProfile(UUID currentUserId);

    void changePassword(UUID currentUserId, ChangePasswordRequest request);

    UserResponse submitKyc(UUID currentUserId, KycSubmitRequest request);

    PaginationResponse<UserResponse> getUsers(
            String role,
            KycStatus kycStatus,
            UserStatus status,
            String keyword,
            int page,
            int size
    );

    UserResponse getUserById(UUID userId);

    UserResponse updateUserStatus(UUID targetUserId, UUID currentAdminId, UpdateUserStatusRequest request);

    UserResponse updateUserRole(UUID targetUserId, UUID currentAdminId, UpdateUserRoleRequest request);

    UserResponse reviewKyc(UUID targetUserId, ReviewKycRequest request);

    void softDeleteUser(UUID targetUserId, UUID currentCallerId, boolean isAdmin);

    UserResponse getRecipientByPhone(String phoneNumber);
}
