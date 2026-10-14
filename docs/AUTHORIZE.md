# TÀI LIỆU MA TRẬN PHÂN QUYỀN (AUTHORIZATION MATRIX)
HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (FINTECH WALLET & PAYMENT GATEWAY)

---

## I. NGUYÊN LÝ BẢO MẬT PHÂN TẦNG (DEFENSE-IN-DEPTH)

Hệ thống kết hợp chặt chẽ giữa 4 cơ chế kiểm soát truy cập:

1. **Authentication & Token Blacklist tại API Gateway (Cổng :80):**
   - Xác thực Access Token (JWT), tra cứu **Sổ đen Redis (`jwt:blacklist:<token>`)** khi user đã Logout.
   - Trích xuất thông tin người dùng và gắn vào Header: `X-User-Id` và `X-User-Roles` trước khi forward xuống downstream services.
   - Xác minh Chữ ký số điện tử HMAC-SHA256 (`X-Signature`) và kiểm tra `X-Timestamp` (chống Replay Attack trong 5 phút).
2. **Phân quyền dựa trên vai trò (RBAC - Role-Based Access Control):**
   - Áp dụng `@EnableMethodSecurity` và `@PreAuthorize("hasRole(...)")` tại tầng Controller.
   - 3 vai trò chính: `ROLE_USER` (Chủ ví), `ROLE_ADMIN` (Quản trị viên), `ROLE_AUDITOR` (Kiểm toán viên).
3. **Phân quyền theo ngữ cảnh / Quyền sở hữu (ABAC - Attribute-Based Access Control):**
   - Không phụ thuộc vào SpEL compiler parameter evaluation flags. Trực tiếp kiểm tra Java thuần dựa trên header `X-User-Id` và `X-User-Roles`: Khách hàng chỉ được phép xem thông tin, số dư ví và lịch sử của chính mình.
4. **Quy tắc Chống lộng quyền & Phòng vệ Hệ thống (Anti-Privilege Escalation):**
   - Admin không thể tự khóa hoặc tự xóa tài khoản của chính mình.
   - Admin không thể khóa, hạ quyền hoặc xóa tài khoản của một Admin khác.
   - Khống chế tối đa **3 tài khoản Admin** trong toàn hệ thống (`ADMIN_LIMIT_EXCEEDED` - mã `4018`).
   - Xóa mềm / Đóng ví: Chỉ được phép đóng ví khi **số dư tài khoản bằng 0 (`balance == 0.00`)**.

---

## II. MA TRẬN PHÂN QUYỀN TOÀN DIỆN 31 APIS (AUTHORIZATION MATRIX)

| STT | Endpoint | Method | Service đảm nhiệm | Quyền hạn (Access Control) | Ràng buộc bảo mật & Nghiệp vụ |
| :---: | :--- | :---: | :--- | :---: | :--- |
| **01** | `/api/v1/auth/register` | `POST` | `wallet-auth` | `PUBLIC` | Đăng ký tài khoản + sinh ví mặc định qua Kafka |
| **02** | `/api/v1/auth/login` | `POST` | `wallet-auth` | `PUBLIC` | Xác thực SĐT/Pass, trả Access/Refresh Token |
| **03** | `/api/v1/auth/refresh` | `POST` | `wallet-auth` | `PUBLIC` | Token Rotation (hủy Refresh Token cũ) |
| **04** | `/api/v1/auth/logout` | `POST` | `wallet-auth` | `USER, ADMIN, AUDITOR` | Đưa Access Token vào Redis Blacklist (mã 5002) |
| **05** | `/api/v1/auth/me` | `GET` | `wallet-auth` | `USER, ADMIN, AUDITOR` | Trả về hồ sơ của chính token đang gọi |
| **06** | `/api/v1/auth/password` | `PUT` | `wallet-auth` | `USER, ADMIN, AUDITOR` | Xác thực mật khẩu cũ trước khi đổi |
| **07** | `/api/v1/auth/kyc` | `POST` | `wallet-auth` | `ROLE_USER` | Nộp hồ sơ eKYC; cho phép nộp lại khi bị REJECTED |
| **08** | `/api/v1/users` | `GET` | `wallet-auth` | `ROLE_ADMIN` | Tra cứu danh sách toàn bộ khách hàng có lọc/phân trang |
| **09** | `/api/v1/users/{userId}` | `GET` | `wallet-auth` | `ADMIN` hoặc Chính chủ (ABAC) | Admin hoặc Chính chủ xem chi tiết hồ sơ cá nhân |
| **10** | `/api/v1/users/{userId}/status` | `PUT` | `wallet-auth` | `ROLE_ADMIN` | Chặn Admin tự khóa mình hoặc khóa Admin khác |
| **11** | `/api/v1/users/{userId}/role` | `PUT` | `wallet-auth` | `ROLE_ADMIN` | Chặn Admin tự hạ quyền; Khống chế tối đa 3 Admin |
| **12** | `/api/v1/users/{userId}/kyc` | `PUT` | `wallet-auth` | `ROLE_ADMIN` | Thẩm định hồ sơ PENDING: VERIFIED hoặc REJECTED kèm lý do |
| **13** | `/api/v1/users/{userId}` | `DELETE` | `wallet-auth` | `ADMIN` hoặc Chính chủ (ABAC) | Xóa mềm tài khoản; chặn Admin tự xóa mình/Admin khác |
| **14** | `/api/v1/accounts/search` | `GET` | `wallet-account` | `USER, ADMIN` | Tra cứu người nhận theo SĐT; không lộ số dư; chặn tự tìm mình |
| **15** | `/api/v1/accounts` | `GET` | `wallet-account` | `ROLE_ADMIN, ROLE_AUDITOR` | Quản trị xem danh sách toàn bộ ví có phân trang & lọc |
| **16** | `/api/v1/accounts/{id}` | `GET` | `wallet-account` | `ADMIN, AUDITOR` hoặc Chính chủ | Xem chi tiết ví + số dư Ledger; chặn xem trộm ví |
| **17** | `/api/v1/accounts/user/{userId}` | `GET` | `wallet-account` | `ADMIN` hoặc Chính chủ (ABAC) | Xem ví mặc định theo User ID |
| **18** | `/api/v1/accounts/{id}/status` | `PUT` | `wallet-account` | `ROLE_ADMIN` | Đóng băng ví (`FROZEN`) hoặc Mở khóa (`ACTIVE`) |
| **19** | `/api/v1/accounts/{id}` | `DELETE` | `wallet-account` | `ADMIN` hoặc Chính chủ (ABAC) | Xóa mềm / Đóng ví (`CLOSED`); Ràng buộc: số dư = 0.00 |
| **20** | `/api/v1/accounts` | `POST` | `wallet-account` | `ROLE_ADMIN` | Tạo ví hệ thống / ví đối tác thủ công |
| | `/api/v1/accounts/me` | `GET` | `wallet-account` | `USER, ADMIN, AUDITOR` | Tra cứu ví của chính mình (tự động lấy từ `X-User-Id`) |
| **21** | `/api/v1/transfers` | `POST` | `wallet-transaction` | `USER, ADMIN` | Bắt buộc `X-Signature` HMAC + `X-Idempotency-Key` (UUID) |
| **22** | `/api/v1/withdrawals` | `POST` | `wallet-transaction` | `USER, ADMIN` | Bắt buộc `X-Signature` HMAC + `X-Idempotency-Key` (UUID) |
| **23** | `/api/v1/transactions/history/{accountId}` | `GET` | `wallet-transaction` | `ADMIN, AUDITOR` hoặc Chính chủ | Lịch sử giao dịch ví cá nhân có phân trang |
| **24** | `/api/v1/transactions` | `GET` | `wallet-transaction` | `ROLE_ADMIN, ROLE_AUDITOR` | Quản trị xem toàn bộ giao dịch toàn hệ thống có phân trang |
| **25** | `/api/v1/transactions/{id}` | `GET` | `wallet-transaction` | `ADMIN, AUDITOR` hoặc Bên liên quan | Chi tiết 1 giao dịch tài chính |
| **26** | `/api/v1/payments/create` | `POST` | `wallet-payment` | `USER, ADMIN` | Khởi tạo URL nạp tiền VNPay Sandbox (tự resolve ví nếu thiếu) |
| **27** | `/api/v1/payments/{provider}/ipn` | `GET` | `wallet-payment` | `PUBLIC` (VNPay Secret Checksum) | Webhook VNPay Server-to-Server cộng tiền và ghi sổ cái |
| **28** | `/api/v1/payments/{provider}/return` | `GET` | `wallet-payment` | `PUBLIC` | Chuyển hướng người dùng sau thanh toán |
| **29** | `/api/v1/ledger/reconciliation` | `GET` | `wallet-ledger` | `ROLE_ADMIN, ROLE_AUDITOR` | Đối soát cân bằng Kế toán kép Realtime (`totalDelta = 0.00`) |
| **30** | `/api/v1/ledger/entries` | `GET` | `wallet-ledger` | `ROLE_ADMIN, ROLE_AUDITOR` | Tra cứu danh sách bút toán Sổ cái kiểm toán |
| **31** | `/api/v1/fraud/alerts` | `GET` | `wallet-fraud-detection` | `ROLE_ADMIN, ROLE_AUDITOR` | Tra cứu danh sách cảnh báo gian lận thời gian thực từ Redis |
