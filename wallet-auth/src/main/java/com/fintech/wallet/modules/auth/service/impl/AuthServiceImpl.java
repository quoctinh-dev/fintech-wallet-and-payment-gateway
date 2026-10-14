package com.fintech.wallet.modules.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.common.exception.ErrorCode;
import com.fintech.wallet.common.outbox.entity.OutboxEvent;
import com.fintech.wallet.common.outbox.repository.OutboxEventRepository;
import com.fintech.wallet.grpc.account.AccountGrpcServiceGrpc.AccountGrpcServiceBlockingStub;
import com.fintech.wallet.grpc.account.CreateAccountRequest;
import com.fintech.wallet.modules.auth.dto.request.LoginRequest;
import com.fintech.wallet.modules.auth.dto.request.RefreshTokenRequest;
import com.fintech.wallet.modules.auth.dto.request.RegisterRequest;
import com.fintech.wallet.modules.auth.dto.response.AuthResponse;
import com.fintech.wallet.modules.auth.entity.RefreshToken;
import com.fintech.wallet.modules.auth.entity.User;
import com.fintech.wallet.modules.auth.enums.KycStatus;
import com.fintech.wallet.modules.auth.enums.UserStatus;
import com.fintech.wallet.modules.auth.repository.BlacklistTokenRepository;
import com.fintech.wallet.modules.auth.repository.RefreshTokenRepository;
import com.fintech.wallet.modules.auth.repository.UserRepository;
import com.fintech.wallet.modules.auth.security.JwtTokenProvider;
import com.fintech.wallet.modules.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistTokenRepository blacklistTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @GrpcClient("wallet-account")
    private AccountGrpcServiceBlockingStub accountGrpcClient;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException(ErrorCode.PHONE_NUMBER_ALREADY_EXISTS);
        }

        User user = User.builder()
                .phoneNumber(request.getPhoneNumber())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.UNVERIFIED)
                .roles("ROLE_USER")
                .build();
        User savedUser = userRepository.save(user);

        accountGrpcClient.createAccount(CreateAccountRequest.newBuilder()
                .setUserId(savedUser.getId().toString())
                .setAccountType("USER_WALLET")
                .setCurrency("VND")
                .build());

        try {
            ObjectNode payloadNode = objectMapper.createObjectNode();
            payloadNode.put("userId", savedUser.getId().toString());
            payloadNode.put("phoneNumber", savedUser.getPhoneNumber());
            payloadNode.put("fullName", savedUser.getFullName());
            payloadNode.put("email", "quoctinhstudy1911@gmail.com");

            String payloadJson = objectMapper.writeValueAsString(payloadNode);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .topic("notification-events")
                    .aggregateId(savedUser.getId().toString())
                    .payload(payloadJson)
                    .status("PENDING")
                    .build();

            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return buildAuthResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 1. Chặn nếu tài khoản đã bị XÓA MỀM (Test Case 13.6)
        if (user.getStatus() == UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
        }

        // 2. Chặn nếu tài khoản bị KHÓA TẠM THỜI (Test Case 2.3)
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (storedToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken);
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Chặn nếu tài khoản bị XÓA MỀM hoặc KHÓA
        if (user.getStatus() == UserStatus.DELETED) {
            refreshTokenRepository.delete(storedToken);
            throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            refreshTokenRepository.delete(storedToken);
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED);
        }

        refreshTokenRepository.delete(storedToken);
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public void logout(String accessToken, String userId) {
        long remainingSecs = jwtTokenProvider.getExpirationInSeconds(accessToken);
        if (remainingSecs > 0) {
            blacklistTokenRepository.blacklistToken(accessToken, remainingSecs);
        }
        refreshTokenRepository.deleteByUserId(UUID.fromString(userId));
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenProvider.generateAccessToken(user.getId(), user.getRoles());
        String refreshTokenString = jwtTokenProvider.generateRefreshToken();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .token(refreshTokenString)
                .userId(user.getId())
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshTokenString)
                .userId(user.getId())
                .fullName(user.getFullName())
                .kycStatus(user.getKycStatus().name())
                .build();
    }
}
