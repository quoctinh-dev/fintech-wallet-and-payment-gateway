package com.fintech.wallet.modules.payment.service.impl;

import com.fintech.wallet.common.enums.PaymentProvider;
import com.fintech.wallet.common.utils.VNPayUtils;
import com.fintech.wallet.grpc.account.AccountGrpcServiceGrpc.AccountGrpcServiceBlockingStub;
import com.fintech.wallet.grpc.account.AccountResponse;
import com.fintech.wallet.grpc.account.GetAccountByUserRequest;
import com.fintech.wallet.grpc.transaction.ConfirmDepositRequest;
import com.fintech.wallet.grpc.transaction.DepositRequest;
import com.fintech.wallet.grpc.transaction.FailDepositRequest;
import com.fintech.wallet.grpc.transaction.TransactionGrpcServiceGrpc.TransactionGrpcServiceBlockingStub;
import com.fintech.wallet.modules.payment.dto.PaymentDepositRequest;
import com.fintech.wallet.modules.payment.gateway.PaymentGateway;
import com.fintech.wallet.modules.payment.gateway.PaymentGatewayRegistry;
import com.fintech.wallet.modules.payment.gateway.dto.PaymentCallbackResult;
import com.fintech.wallet.modules.payment.gateway.dto.PaymentGatewayRequest;
import com.fintech.wallet.modules.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentGatewayRegistry gatewayRegistry;

    @GrpcClient("wallet-transaction")
    private TransactionGrpcServiceBlockingStub transactionGrpcClient;

    @GrpcClient("wallet-account")
    private AccountGrpcServiceBlockingStub accountGrpcClient;

    @Override
    public String createPaymentUrl(PaymentDepositRequest request, HttpServletRequest servletRequest) {

        // Nếu client không truyền accountId, tự động truy vấn ví USER_WALLET mặc định của chính người gọi
        if (request.getAccountId() == null) {
            UUID currentUserId = extractCurrentUserId(servletRequest);
            AccountResponse defaultWallet = accountGrpcClient.getAccountByUserIdAndType(
                    GetAccountByUserRequest.newBuilder()
                            .setUserId(currentUserId.toString())
                            .setAccountType("USER_WALLET")
                            .build()
            );
            request.setAccountId(UUID.fromString(defaultWallet.getId()));
            log.info("[Payment] Đã tự động nhận diện ví mặc định USER_WALLET: {} cho User: {}", request.getAccountId(),
                    currentUserId);
        }

        // Gọi gRPC sang Transaction tạo giao dịch Pending
        transactionGrpcClient.createPendingDeposit(DepositRequest.newBuilder()
                .setAccountId(request.getAccountId().toString())
                .setAmount(request.getAmount().doubleValue())
                .setIdempotencyKey(request.getIdempotencyKey())
                .setDescription(request.getDescription() != null ? request.getDescription() : "Nạp tiền")
                .build());

        PaymentGateway gateway = gatewayRegistry.getGateway(request.getProvider());
        PaymentGatewayRequest gatewayRequest = PaymentGatewayRequest.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .amount(request.getAmount())
                .description(request.getDescription() != null ? request.getDescription() : "Nạp tiền")
                .accountId(request.getAccountId())
                .clientIp(VNPayUtils.getIpAddress(servletRequest))
                .build();

        return gateway.createPaymentUrl(gatewayRequest);
    }

    @Override
    public Map<String, String> processIPN(PaymentProvider provider, Map<String, String> params) {
        Map<String, String> ackResponse = new HashMap<>();
        try {
            PaymentGateway gateway = gatewayRegistry.getGateway(provider);
            PaymentCallbackResult result = gateway.processIPN(params);

            if (!result.isValidSignature()) {
                ackResponse.put("RspCode", result.getAckCode());
                ackResponse.put("Message", result.getAckMessage());
                return ackResponse;
            }

            if (result.isPaymentSuccess()) {
                transactionGrpcClient.confirmDeposit(ConfirmDepositRequest.newBuilder()
                        .setIdempotencyKey(result.getIdempotencyKey()).build());
            } else {
                transactionGrpcClient.failDeposit(FailDepositRequest.newBuilder()
                        .setIdempotencyKey(result.getIdempotencyKey()).build());
            }

            ackResponse.put("RspCode", result.getAckCode());
            ackResponse.put("Message", result.getAckMessage());
            return ackResponse;

        } catch (Exception e) {
            log.error("[PaymentService] Lỗi hệ thống: ", e);
            ackResponse.put("RspCode", "99");
            ackResponse.put("Message", "Unknown error");
            return ackResponse;
        }
    }

    @Override
    public boolean verifyReturn(PaymentProvider provider, Map<String, String> params) {
        return gatewayRegistry.getGateway(provider).verifyReturn(params);
    }

    private UUID extractCurrentUserId(HttpServletRequest servletRequest) {
        String headerUserId = servletRequest != null ? servletRequest.getHeader("X-User-Id") : null;
        if (headerUserId != null && !headerUserId.isBlank()) {
            return UUID.fromString(headerUserId);
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return (principal instanceof UUID) ? (UUID) principal : UUID.fromString((String) principal);
    }
}
