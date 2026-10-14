package com.fintech.wallet.modules.account.enums;

public enum AccountStatus {
    ACTIVE,   // Đang hoạt động bình thường, được phép giao dịch
    FROZEN,   // Đang bị đóng băng khẩn cấp (chặn mọi giao dịch chuyển/rút tiền đi)
    CLOSED    // Đã đóng ví vĩnh viễn
}
