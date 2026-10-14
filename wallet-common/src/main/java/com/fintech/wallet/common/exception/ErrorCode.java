package com.fintech.wallet.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ==========================================
    // 1. Common Errors (1000 - 1999)
    // ==========================================
    INVALID_INPUT(1000, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST_BODY(1001, "Nội dung yêu cầu (Body) không hợp lệ hoặc sai định dạng JSON", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR(1999, "Lỗi hệ thống, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),

    // ==========================================
    // 2. Account Module Errors (2000 - 2999)
    // ==========================================
    ACCOUNT_NOT_FOUND(2001, "Tài khoản không tồn tại", HttpStatus.NOT_FOUND),
    ACCOUNT_ALREADY_EXISTS(2002, "Tài khoản đã tồn tại cho người dùng này", HttpStatus.CONFLICT),
    ACCOUNT_FROZEN(2003, "Tài khoản ví đang bị đóng băng khẩn cấp do nghi vấn gian lận", HttpStatus.FORBIDDEN),
    ACCOUNT_CLOSED(2004, "Tài khoản ví đã bị đóng vĩnh viễn", HttpStatus.FORBIDDEN),
    RECIPIENT_ACCOUNT_NOT_FOUND(2005, "Không tìm thấy ví người nhận hoặc ví người nhận không khả dụng", HttpStatus.NOT_FOUND),
    CANNOT_TRANSFER_TO_SELF(2006, "Không thể chuyển tiền cho chính tài khoản của bạn", HttpStatus.BAD_REQUEST),
    INVALID_ACCOUNT_STATUS(2007, "Trạng thái tài khoản ví không hợp lệ", HttpStatus.BAD_REQUEST),
    ACCOUNT_CANNOT_BE_CLOSED_WITH_BALANCE(2008, "Không thể đóng ví khi số dư vẫn còn tiền", HttpStatus.BAD_REQUEST),

    // ==========================================
    // 3. Transaction Module Errors (3000 - 3999)
    // ==========================================
    INSUFFICIENT_BALANCE(3001, "Số dư tài khoản không đủ", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_DUPLICATED(3002, "Giao dịch đang được xử lý hoặc đã hoàn tất", HttpStatus.CONFLICT),
    TRANSACTION_NOT_FOUND(3003, "Không tìm thấy thông tin giao dịch", HttpStatus.NOT_FOUND),

    // ==========================================
    // 4. Auth & User Module Errors (4000 - 4999)
    // ==========================================
    PHONE_NUMBER_ALREADY_EXISTS(4001, "Số điện thoại đã tồn tại trên hệ thống", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(4002, "Số điện thoại hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    USER_NOT_FOUND(4003, "Không tìm thấy thông tin người dùng", HttpStatus.NOT_FOUND),
    ACCOUNT_BLOCKED(4004, "Tài khoản của bạn đã bị khóa bởi Quản trị viên", HttpStatus.FORBIDDEN),
    INVALID_OLD_PASSWORD(4005, "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
    CITIZEN_ID_ALREADY_EXISTS(4006, "Số CCCD/Hộ chiếu này đã được đăng ký", HttpStatus.CONFLICT),
    KYC_ALREADY_SUBMITTED(4007, "Hồ sơ eKYC đang ở trạng thái chờ duyệt hoặc đã xác thực", HttpStatus.CONFLICT),
    CANNOT_BLOCK_SELF(4008, "Quản trị viên không thể tự khóa tài khoản của chính mình", HttpStatus.BAD_REQUEST),
    CANNOT_BLOCK_ANOTHER_ADMIN(4009, "Không có quyền khóa tài khoản của Quản trị viên khác", HttpStatus.BAD_REQUEST),
    CANNOT_DEMOTE_SELF(4010, "Quản trị viên không thể tự hạ quyền của chính mình", HttpStatus.BAD_REQUEST),
    CANNOT_MODIFY_ANOTHER_ADMIN(4011, "Không có quyền thay đổi vai trò của Quản trị viên khác", HttpStatus.BAD_REQUEST),
    KYC_NOT_IN_PENDING_STATE(4012, "Chỉ có thể phê duyệt hồ sơ eKYC đang ở trạng thái PENDING", HttpStatus.BAD_REQUEST),
    ACCOUNT_DELETED(4014, "Tài khoản của bạn đã bị đóng vĩnh viễn", HttpStatus.FORBIDDEN),
    CANNOT_DELETE_SELF(4015, "Quản trị viên không thể tự xóa tài khoản của chính mình", HttpStatus.BAD_REQUEST),
    CANNOT_DELETE_ANOTHER_ADMIN(4016, "Không có quyền xóa tài khoản của Quản trị viên khác", HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_SAME_AS_OLD(4017, "Mật khẩu mới không được trùng với mật khẩu cũ", HttpStatus.BAD_REQUEST),
    ADMIN_LIMIT_EXCEEDED(4018, "Số lượng Quản trị viên đã đạt giới hạn tối đa của hệ thống (tối đa 3 Admin)", HttpStatus.BAD_REQUEST),

    // ==========================================
    // 5. Security & Gateway Errors (5000 - 5999)
    // ==========================================
    UNAUTHORIZED(5001, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),
    TOKEN_REVOKED(5002, "Token đã bị thu hồi do đăng xuất", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(5003, "Bạn không có quyền truy cập tài nguyên này", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}