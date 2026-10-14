
package com.fintech.wallet.modules.payment.controller;

import com.fintech.wallet.common.dto.ApiResponse;
import com.fintech.wallet.common.enums.PaymentProvider;
import com.fintech.wallet.modules.payment.dto.PaymentDepositRequest;
import com.fintech.wallet.modules.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(
        name = "05. Payment Gateway API (Cổng Thanh toán & Nạp tiền)",
        description = "Khởi tạo URL nạp tiền VNPay Sandbox, xử lý Webhook IPN Server-to-Server và Return URL"
)
public class PaymentController {

    private final PaymentService paymentService;

    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @PostMapping("/create")
    @Operation(
            summary = "26. Khởi tạo liên kết nạp tiền (VNPay Sandbox)",
            description = "Tạo yêu cầu nạp tiền, lưu giao dịch PENDING và sinh URL cổng thanh toán có chữ ký SecureHash HMAC- SHA512"
            )
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<ApiResponse<String>> createPaymentUrl(
            @Valid @RequestBody PaymentDepositRequest request,
            HttpServletRequest servletRequest) {
        String paymentUrl = paymentService.createPaymentUrl(request, servletRequest);
        return ResponseEntity.ok(ApiResponse.success("Tạo URL thanh toán thành công", paymentUrl));
    }

    @GetMapping("/{provider}/ipn")
    @Operation(
            summary = "27. Xử lý Webhook IPN từ cổng thanh toán",
            description = "Endpoint công khai dành cho máy chủ VNPay gọi tự động (Server-to-Server) xác nhận kết quả nạp tiền"
    )
    public ResponseEntity<Map<String, String>> handleIPN(
            @Parameter(description = "Nhà cung cấp cổng thanh toán", example = "VNPAY")
            @PathVariable("provider") PaymentProvider provider,
            @RequestParam Map<String, String> queryParams) {
        Map<String, String> response = paymentService.processIPN(provider, queryParams);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{provider}/return")
    @Operation(
            summary = "28. Xử lý Return URL (Chuyển hướng trình duyệt)",
            description = "Điều hướng trình duyệt của khách hàng trở lại giao diện web ví sau khi hoàn tất giao dịch tại ngân hàng"
            )
    public ResponseEntity<Map<String, Object>> handleReturn(
            @Parameter(description = "Nhà cung cấp cổng thanh toán", example = "VNPAY")
            @PathVariable("provider") PaymentProvider provider,
            @RequestParam Map<String, String> queryParams) {
        boolean isValid = paymentService.verifyReturn(provider, queryParams);
        String responseCode = queryParams.get("vnp_ResponseCode");

        Map<String, Object> response = new HashMap<>();
        if (isValid && "00".equals(responseCode)) {
            response.put("status", "SUCCESS");
            response.put("message", "Thanh toán thành công");
            response.put("transactionId", queryParams.get("vnp_TxnRef"));
            return ResponseEntity.ok(response);
        }
        response.put("status", "FAILED");
        response.put("message", isValid ? "Giao dịch thất bại từ ngân hàng" : "Chữ ký không hợp lệ");
        response.put("errorCode", responseCode);
        return ResponseEntity.badRequest().body(response);
    }
}
