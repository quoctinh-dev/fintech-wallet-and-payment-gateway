package com.fintech.wallet.modules.account.grpc;

import com.fintech.wallet.common.exception.BusinessException;
import com.fintech.wallet.grpc.account.AccountGrpcServiceGrpc;
import com.fintech.wallet.grpc.account.AccountResponse;
import com.fintech.wallet.grpc.account.CreateAccountRequest;
import com.fintech.wallet.grpc.account.GetAccountRequest;
import com.fintech.wallet.modules.account.enums.AccountType;
import com.fintech.wallet.modules.account.service.AccountService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class AccountGrpcController extends AccountGrpcServiceGrpc.AccountGrpcServiceImplBase {

    private final AccountService accountService;

    @Override
    public void getAccountWithLock(GetAccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        try {
            //1. Gọi service để lấy thông tin account với lock
            var accountDto = accountService.getAccountWithLock(UUID.fromString(request.getAccountId()));

            // 2. Build đối tượng Protobuf trả về
            AccountResponse response = AccountResponse.newBuilder()
                    .setId(accountDto.getId().toString())
                    .setUserId(accountDto.getUserId().toString())
                    .setAccountType(accountDto.getAccountType().name())
                    .setCurrency(accountDto.getCurrency())
                    .setBalance(accountDto.getBalance().doubleValue())
                    .build();

            // 3. Bắn kết quả về cho Client (Transaction)
            responseObserver.onNext(response);
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
    public void createAccount(CreateAccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        try {
            com.fintech.wallet.modules.account.dto.request.CreateAccountRequest localRequest =
                    new com.fintech.wallet.modules.account.dto.request.CreateAccountRequest();
            localRequest.setUserId(UUID.fromString(request.getUserId()));
            localRequest.setAccountType(AccountType.valueOf(request.getAccountType()));
            localRequest.setCurrency(request.getCurrency());

            var accountDto = accountService.createAccount(localRequest);

            AccountResponse response = AccountResponse.newBuilder()
                    .setId(accountDto.getId().toString())
                    .setUserId(accountDto.getUserId().toString())
                    .setAccountType(accountDto.getAccountType().name())
                    .setCurrency(accountDto.getCurrency())
                    .setBalance(accountDto.getBalance().doubleValue())
                    .build();

            responseObserver.onNext(response);
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
    public void getAccountById(GetAccountRequest request, StreamObserver<AccountResponse> responseObserver) {
        try {
            var accountDto = accountService.getAccountById(UUID.fromString(request.getAccountId()));
            AccountResponse response = AccountResponse.newBuilder()
                    .setId(accountDto.getId().toString())
                    .setUserId(accountDto.getUserId().toString())
                    .setAccountType(accountDto.getAccountType().name())
                    .setCurrency(accountDto.getCurrency())
                    .setBalance(accountDto.getBalance().doubleValue())
                    .build();
            responseObserver.onNext(response);
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
    public void getAccountByUserIdAndType(com.fintech.wallet.grpc.account.GetAccountByUserRequest request, StreamObserver<AccountResponse> responseObserver) {
        try {
            var accountDto = accountService.getAccountByUserIdAndType(
                    UUID.fromString(request.getUserId()),
                    AccountType.valueOf(request.getAccountType())
            );
            AccountResponse response = AccountResponse.newBuilder()
                    .setId(accountDto.getId().toString())
                    .setUserId(accountDto.getUserId().toString())
                    .setAccountType(accountDto.getAccountType().name())
                    .setCurrency(accountDto.getCurrency())
                    .setBalance(accountDto.getBalance().doubleValue())
                    .build();
            responseObserver.onNext(response);
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



