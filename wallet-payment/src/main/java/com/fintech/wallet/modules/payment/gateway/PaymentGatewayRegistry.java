package com.fintech.wallet.modules.payment.gateway;

import com.fintech.wallet.common.enums.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry tập trung tất cả PaymentGateway implementations.
 *
 * <p>Spring tự động inject tất cả bean implements {@link PaymentGateway}.
 * Để thêm gateway mới chỉ cần tạo class implements PaymentGateway + @Service,
 * không cần sửa class này.
 *
 * <p>Ví dụ dùng:
 * <pre>
 *   PaymentGateway gateway = registry.getGateway(PaymentProvider.VNPAY);
 *   String url = gateway.createPaymentUrl(request);
 * </pre>
 */
@Slf4j
@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentProvider, PaymentGateway> gatewayMap;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.gatewayMap = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::getProvider, Function.identity()));
        log.info("[PaymentGatewayRegistry] Đã đăng ký {} gateway(s): {}",
                gatewayMap.size(), gatewayMap.keySet());
    }

    /**
     * Lấy gateway tương ứng với provider.
     *
     * @throws IllegalArgumentException nếu provider chưa được implement
     */
    public PaymentGateway getGateway(PaymentProvider provider) {
        PaymentGateway gateway = gatewayMap.get(provider);
        if (gateway == null) {
            throw new IllegalArgumentException(
                    "Chưa có implementation cho payment provider: " + provider +
                    ". Các provider đang hỗ trợ: " + gatewayMap.keySet());
        }
        return gateway;
    }
}
