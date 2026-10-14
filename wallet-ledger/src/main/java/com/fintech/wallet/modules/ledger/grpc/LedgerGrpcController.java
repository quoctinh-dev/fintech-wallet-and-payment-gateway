package com.fintech.wallet.modules.ledger.grpc;

import com.fintech.wallet.grpc.ledger.*;
import com.fintech.wallet.modules.ledger.entity.LedgerEntry;
import com.fintech.wallet.modules.ledger.repository.LedgerEntryRepository;
import com.fintech.wallet.modules.ledger.service.LedgerService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@GrpcService
@RequiredArgsConstructor
public class LedgerGrpcController extends LedgerGrpcServiceGrpc.LedgerGrpcServiceImplBase {

    private final LedgerService ledgerService;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    public void getAccountBalance(GetBalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
        try {
            // Bước 1: Lấy accountId (dạng String) từ Request, biến thành UUID, rồi đưa cho hàm Java cũ xử lý
            var balance = ledgerService.getAccountBalance(UUID.fromString(request.getAccountId()));

            // Bước 2: Lấy số dư (BigDecimal) từ hàm cũ, đóng gói vào cái hộp BalanceResponse của gRPC
            BalanceResponse response = BalanceResponse.newBuilder()
                    .setBalance(balance.doubleValue())
                    .build();

            // Bước 3: Giao kiện hàng cho bưu tá (StreamObserver) để ship qua mạng về lại cho Client
            responseObserver.onNext(response);
            responseObserver.onCompleted(); // Báo hiệu đã giao xong
        } catch (Exception e) {
            // Nếu có lỗi (VD: Không tìm thấy DB), quăng lỗi qua mạng về cho Client
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }

    @Override
    public void recordEntry(com.fintech.wallet.grpc.ledger.RecordEntryRequest request, StreamObserver<com.fintech.wallet.grpc.ledger.RecordEntryResponse>
            responseObserver) {
        try {
            ledgerService.recordEntry(
                    UUID.fromString(request.getTransactionId()),
                    UUID.fromString(request.getAccountId()),
                    java.math.BigDecimal.valueOf(request.getAmount())
            );

            com.fintech.wallet.grpc.ledger.RecordEntryResponse response = com.fintech.wallet.grpc.ledger.RecordEntryResponse.newBuilder()
                    .setSuccess(true)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getAccountTransactions(
            GetAccountTransactionsRequest request,
            StreamObserver<GetAccountTransactionsResponse> responseObserver) {

        try {
            Pageable pageable = PageRequest.of(
                    request.getPage(),
                    request.getSize(),
                    Sort.by("createdAt").descending()
            );

            Page<LedgerEntry> page = ledgerEntryRepository.findByAccountId(
                    UUID.fromString(request.getAccountId()),
                    pageable
            );

            GetAccountTransactionsResponse.Builder responseBuilder =
                    GetAccountTransactionsResponse.newBuilder()
                            .setTotalElements(page.getTotalElements())
                            .setTotalPages(page.getTotalPages());

            for (LedgerEntry entry : page.getContent()) {
                LedgerEntryMessage ledgerEntryMessage = LedgerEntryMessage.newBuilder()
                        .setTransactionId(entry.getTransactionId().toString())
                        .setAmount(entry.getAmount().doubleValue())
                        .setCreatedAt(
                                entry.getCreatedAt() != null
                                        ? entry.getCreatedAt().toString()
                                        : ""
                        )
                        .build();

                responseBuilder.addEntries(ledgerEntryMessage);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

}
