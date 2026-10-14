package com.fintech.wallet.modules.transaction.grpc;

import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.grpc.transaction.ConfirmDepositRequest;
import com.fintech.wallet.grpc.transaction.DepositRequest;
import com.fintech.wallet.grpc.transaction.EmptyResponse;
import com.fintech.wallet.grpc.transaction.FailDepositRequest;
import com.fintech.wallet.grpc.transaction.TransactionGrpcServiceGrpc;
import com.fintech.wallet.modules.transaction.service.TransactionService;
import io.grpc.Status;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class TransactionGrpcController extends TransactionGrpcServiceGrpc.TransactionGrpcServiceImplBase {

    private final TransactionService transactionService;
    private final HealthStatusManager healthStatusManager;

    @Override
    public void createPendingDeposit(DepositRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            com.fintech.wallet.modules.transaction.dto.request.DepositRequest localReq = new com.fintech.wallet.modules.transaction.dto.request.DepositRequest();
            localReq.setAccountId(UUID.fromString(request.getAccountId()));
            localReq.setAmount(BigDecimal.valueOf(request.getAmount()));
            localReq.setIdempotencyKey(request.getIdempotencyKey());
            localReq.setDescription(request.getDescription());

            transactionService.createPendingDeposit(localReq);

            responseObserver.onNext(EmptyResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getErrorCode().name())
                    .asRuntimeException());
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void confirmDeposit(ConfirmDepositRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            transactionService.confirmDeposit(request.getIdempotencyKey());
            responseObserver.onNext(EmptyResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getErrorCode().name())
                    .asRuntimeException());
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void failDeposit(FailDepositRequest request, StreamObserver<EmptyResponse> responseObserver) {
        try {
            transactionService.failDeposit(request.getIdempotencyKey());
            responseObserver.onNext(EmptyResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getErrorCode().name())
                    .asRuntimeException());
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }
}
