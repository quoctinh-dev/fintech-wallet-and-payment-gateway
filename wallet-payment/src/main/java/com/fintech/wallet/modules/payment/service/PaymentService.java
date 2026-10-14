package com.fintech.wallet.modules.payment.service;

import com.fintech.wallet.common.enums.PaymentProvider;
import com.fintech.wallet.modules.payment.dto.PaymentDepositRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public interface PaymentService {
    String createPaymentUrl(PaymentDepositRequest request, HttpServletRequest servletRequest);
    Map<String, String> processIPN(PaymentProvider provider, Map<String, String> params);
    boolean verifyReturn(PaymentProvider provider, Map<String, String> params);
}
