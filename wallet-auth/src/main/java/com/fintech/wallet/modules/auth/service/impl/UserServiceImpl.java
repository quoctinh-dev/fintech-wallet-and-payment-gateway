package com.fintech.wallet.modules.auth.service.impl;

import com.fintech.wallet.common.dto.PaginationResponse;
import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.common.exception.ErrorCode;
import com.fintech.wallet.modules.auth.dto.request.ChangePasswordRequest;
import com.fintech.wallet.modules.auth.dto.request.KycSubmitRequest;
import com.fintech.wallet.modules.auth.dto.request.ReviewKycRequest;
import com.fintech.wallet.modules.auth.dto.request.UpdateUserRoleRequest;
import com.fintech.wallet.modules.auth.dto.request.UpdateUserStatusRequest;
import com.fintech.wallet.modules.auth.dto.response.UserResponse;
import com.fintech.wallet.modules.auth.entity.User;
import com.fintech.wallet.modules.auth.enums.KycStatus;
import com.fintech.wallet.modules.auth.enums.UserStatus;
import com.fintech.wallet.modules.auth.repository.RefreshTokenRepository;
import com.fintech.wallet.modules.auth.repository.UserRepository;
import com.fintech.wallet.modules.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID currentUserId) {
        User user = findUserByIdOrThrow(currentUserId);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(UUID currentUserId, ChangePasswordRequest request) {
        User user = findUserByIdOrThrow(currentUserId);

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_OLD_PASSWORD);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenRepository.deleteByUserId(currentUserId);
    }

    @Override
    @Transactional
    public UserResponse submitKyc(UUID currentUserId, KycSubmitRequest request) {
        User user = findUserByIdOrThrow(currentUserId);

        // 1. Chặn nếu hồ sơ đang PENDING (chờ duyệt) hoặc VERIFIED (đã duyệt xong)
        if (user.getKycStatus() == KycStatus.PENDING || user.getKycStatus() == KycStatus.VERIFIED) {
            throw new BusinessException(ErrorCode.KYC_ALREADY_SUBMITTED);
        }

        // 2. Chặn nếu CCCD này đã bị người khác sử dụng (loại trừ chính user hiện tại để cho phép NỘP LẠI)
        if (userRepository.existsByCitizenIdAndIdNot(request.getCitizenId(), currentUserId)) {
            throw new BusinessException(ErrorCode.CITIZEN_ID_ALREADY_EXISTS);
        }

        user.setCitizenId(request.getCitizenId());
        user.setIdCardFrontUrl(request.getIdCardFrontUrl());
        user.setIdCardBackUrl(request.getIdCardBackUrl());
        user.setKycStatus(KycStatus.PENDING);
        user.setKycRejectionReason(null); // Xóa lý do từ chối cũ khi nộp lại

        User savedUser = userRepository.save(user);
        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginationResponse<UserResponse> getUsers(
            String role,
            KycStatus kycStatus,
            UserStatus status,
            String keyword,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.searchUsers(role, kycStatus, status, keyword, pageable);

        return PaginationResponse.fromPage(userPage.map(this::mapToUserResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        User user = findUserByIdOrThrow(userId);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(UUID targetUserId, UUID currentAdminId, UpdateUserStatusRequest request) {
        User targetUser = findUserByIdOrThrow(targetUserId);

        if (targetUserId.equals(currentAdminId)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }

        if ("ROLE_ADMIN".equalsIgnoreCase(targetUser.getRoles())) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_ANOTHER_ADMIN);
        }

        targetUser.setStatus(request.getStatus());
        User updatedUser = userRepository.save(targetUser);

        if (request.getStatus() == UserStatus.BLOCKED || request.getStatus() == UserStatus.DELETED) {
            refreshTokenRepository.deleteByUserId(targetUserId);
        }

        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(UUID targetUserId, UUID currentAdminId, UpdateUserRoleRequest request) {
        User targetUser = findUserByIdOrThrow(targetUserId);

        // 1. Chặn Admin tự hạ quyền của chính mình
        if (targetUserId.equals(currentAdminId)) {
            throw new BusinessException(ErrorCode.CANNOT_DEMOTE_SELF);
        }

        // 2. Chặn Admin can thiệp hoặc thay đổi vai trò của Admin khác
        if ("ROLE_ADMIN".equalsIgnoreCase(targetUser.getRoles())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_ANOTHER_ADMIN);
        }

        // 3. Khống chế tối đa 3 Admin trong hệ thống để chống phá hoại tạo hàng loạt
        if ("ROLE_ADMIN".equalsIgnoreCase(request.getRole())) {
            long adminCount = userRepository.searchUsers("ROLE_ADMIN", null, null, null, PageRequest.of(0, 1)).getTotalElements();
            if (adminCount >= 3) {
                throw new BusinessException(ErrorCode.ADMIN_LIMIT_EXCEEDED);
            }
        }

        targetUser.setRoles(request.getRole());
        User updatedUser = userRepository.save(targetUser);
        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse reviewKyc(UUID targetUserId, ReviewKycRequest request) {
        User targetUser = findUserByIdOrThrow(targetUserId);

        if (targetUser.getKycStatus() != KycStatus.PENDING) {
            throw new BusinessException(ErrorCode.KYC_NOT_IN_PENDING_STATE);
        }

        if (request.getStatus() == KycStatus.REJECTED) {
            if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            targetUser.setKycStatus(KycStatus.REJECTED);
            targetUser.setKycRejectionReason(request.getRejectionReason());
        } else if (request.getStatus() == KycStatus.VERIFIED) {
            targetUser.setKycStatus(KycStatus.VERIFIED);
            targetUser.setKycRejectionReason(null);
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User updatedUser = userRepository.save(targetUser);
        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public void softDeleteUser(UUID targetUserId, UUID currentCallerId, boolean isAdmin) {
        User targetUser = findUserByIdOrThrow(targetUserId);

        if (isAdmin) {
            if (targetUserId.equals(currentCallerId)) {
                throw new BusinessException(ErrorCode.CANNOT_DELETE_SELF);
            }
            if ("ROLE_ADMIN".equalsIgnoreCase(targetUser.getRoles())) {
                throw new BusinessException(ErrorCode.CANNOT_DELETE_ANOTHER_ADMIN);
            }
        }

        targetUser.setStatus(UserStatus.DELETED);
        userRepository.save(targetUser);

        refreshTokenRepository.deleteByUserId(targetUserId);
    }


    @Override
    @Transactional(readOnly = true)
    public UserResponse getRecipientByPhone(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.DELETED || user.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }

        return mapToUserResponse(user);
    }


    private User findUserByIdOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .phoneNumber(user.getPhoneNumber())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .citizenId(user.getCitizenId())
                .idCardFrontUrl(user.getIdCardFrontUrl())
                .idCardBackUrl(user.getIdCardBackUrl())
                .kycStatus(user.getKycStatus())
                .kycRejectionReason(user.getKycRejectionReason())
                .roles(user.getRoles())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
