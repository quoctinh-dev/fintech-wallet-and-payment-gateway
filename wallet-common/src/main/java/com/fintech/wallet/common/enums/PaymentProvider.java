package com.fintech.wallet.common.enums;

/**
 * Danh sách các cổng thanh toán được hỗ trợ.
 * Thêm provider mới vào đây khi tích hợp (MOMO, ZALOPAY, STRIPE, ...)
 */
public enum PaymentProvider {
    VNPAY,
    MOMO,       // Placeholder — chưa implement
    ZALOPAY     // Placeholder — chưa implement
}
