# DANH SÁCH CHỨC NĂNG API & QUY TẮC RÀNG BUỘC NGHIỆP VỤ (FUNCTION LIST)
HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN TOÀN DIỆN (FINTECH WALLET & PAYMENT GATEWAY)

---

## I. MÔ HÌNH PHÂN QUYỀN TRUY CẬP (ROLE-BASED & ATTRIBUTE-BASED ACCESS CONTROL - RBAC / ABAC)

Hệ thống thiết lập 3 vai trò người dùng với các ranh giới bảo mật nghiêm ngặt:

1. **`ROLE_USER` (Khách hàng cá nhân / Chủ ví):**
   - Đăng ký, đăng nhập, nộp hồ sơ eKYC (cho phép nộp lại khi bị từ chối), đổi mật khẩu và quản lý hồ sơ cá nhân (`/me`), yêu cầu xóa mềm tài khoản cá nhân.
   - Quản lý ví cá nhân: Tra cứu ví của chính mình qua `/api/v1/accounts/me`, tra cứu số dư thời gian thực từ Sổ cái, tìm kiếm tên người nhận bằng Số điện thoại, yêu cầu đóng ví (xóa mềm ví khi số dư = 0.00).
   - Giao dịch tài chính: Nạp tiền (VNPay Sandbox), Rút tiền về ngân hàng, Chuyển tiền P2P tức thì.
   - Tra cứu lịch sử giao dịch và biến động số dư của chính tài khoản mình sở hữu.

2. **`ROLE_ADMIN` (Quản trị viên vận hành hệ thống):**
   - Quản trị người dùng: Tra cứu danh sách toàn bộ khách hàng, xem chi tiết, khóa/mở khóa tài khoản, phân quyền Role (khống chế tối đa 3 Admin), phê duyệt/từ chối hồ sơ eKYC, xóa mềm tài khoản vi phạm.
   - Quản trị tài khoản ví: Xem danh sách phân trang toàn bộ ví hệ thống, tạo ví hệ thống thủ công, đóng băng tạm thời ví có dấu hiệu nghi vấn gian lận (`FROZEN`), đóng ví vi phạm (`CLOSED`).
   - Quản trị giao dịch & Dashboard: Tra cứu toàn bộ giao dịch trên toàn hệ thống có lọc phân trang; xem các chỉ số thống kê tài chính tổng quan.

3. **`ROLE_AUDITOR` (Kiểm toán viên tài chính & Giám sát an ninh):**
   - Chuyên trách kiểm toán độc lập:
   - Thực hiện đối soát Sổ cái Kế toán kép thời gian thực (Real-time Financial Reconciliation).
   - Tra cứu toàn bộ các bút toán chi tiết (Ledger Entries).
   - Giám sát luồng cảnh báo rủi ro & gian lận thời gian thực (Fraud Detection Alerts).

---

## II. CÁC NGUYÊN TẮC RÀNG BUỘC BẢO MẬT & NGHIỆP VỤ BẮT BUỘC (BUSINESS CONSTRAINTS)

### 1. Quy tắc Tự truy cập & Chống lộng quyền (Self-Access & Anti-Privilege Escalation)
- **User thường (Chính chủ):** Chỉ được phép xem số dư ví, thông tin tài khoản và lịch sử giao dịch của CHÍNH MÌNH (`X-User-Id == userId`). Hệ thống chặn đứng mọi hành vi đoán UUID để xem trộm ví người khác.
- **Admin không được tự hại:** ADMIN không được tự khóa tài khoản hoặc tự xóa tài khoản của chính mình (`targetUserId != currentAdminId`).
- **Admin không được hạ quyền hoặc xóa Admin khác:** ADMIN không được phép thay đổi Role, khóa hoặc xóa tài khoản của một ADMIN khác để ngăn chặn xung đột nội bộ.
- **Khống chế số lượng Admin:** Toàn hệ thống khống chế cứng tối đa 3 tài khoản Admin (`ADMIN_LIMIT_EXCEEDED` - mã lỗi `4018`) nhằm chống tấn công leo thang đặc quyền và phá hoại hệ thống.
- **Bảo vệ danh tính cá nhân:** Endpoint `/api/v1/auth/me` và `/api/v1/accounts/me` chỉ trả về thông tin của token đang gọi, kể cả Admin.

### 2. Quy tắc Toàn vẹn Tài chính, Kế toán Kép & Xóa mềm Ví (Financial Integrity & Soft Delete)
- Số tiền giao dịch (`amount`) bắt buộc phải là số dương (`amount > 0`), giá trị tối thiểu là 1,000 VNĐ (Rút tiền tối thiểu 50,000 VNĐ).
- Số dư ví người dùng không bao giờ được phép âm (`balance >= amount`).
- Sổ cái (`ledger_entries`) là cơ sở dữ liệu bất biến (Append-Only) — **Tuyệt đối không có API sửa (UPDATE) hay xóa (DELETE) các bút toán Ledger**.
- Mọi giao dịch chuyển tiền phải ghi đủ 2 dòng đối xứng: Nợ (Debit) và Có (Credit) với tổng độ lệch bằng đúng `0.00`.
- **Nguyên tắc Xóa mềm / Đóng ví (`CLOSED`):** Tuyệt đối không xóa cứng (Hard Delete) ví khỏi database để bảo vệ tính toàn vẹn dữ liệu kế toán quá khứ. Khách hàng hoặc Admin chỉ được phép đóng ví khi **số dư tài khoản bằng 0 (`balance == 0.00`)**. Nếu ví còn tiền, hệ thống chặn đứng yêu cầu (`ACCOUNT_CANNOT_BE_CLOSED_WITH_BALANCE` - mã `2008`).

### 3. Quy tắc Bảo mật Đa tầng & Phòng chống Rủi ro (Defense-in-Depth)
- Mọi giao dịch thay đổi số dư (`/transfers`, `/withdrawals`) bắt buộc phải có chữ ký điện tử `X-Signature` (HMAC-SHA256) được xác thực tại API Gateway.
- Bắt buộc gửi kèm `X-Idempotency-Key` (UUID) để ngăn chặn trừ tiền 2 lần khi bấm nút liên tiếp hoặc mạng lag.
- Token sau khi `/logout` sẽ bị đưa vào Sổ đen (Redis Blacklist). Mọi request sau đó mang token này đều bị Gateway từ chối ngay lập tức (`401 Unauthorized`).
- Engine phát hiện gian lận (Python FastAPI) tự động đánh dấu rủi ro với giao dịch `>= 50,000,000 VNĐ` hoặc tần suất `> 5 giao dịch/phút`.

---

## III. DANH SÁCH TOÀN DIỆN 31 CHỨC NĂNG API (100% HOÀN THÀNH & PASS TEST)

---

### PHÂN HỆ 1: AUTHENTICATION & USER MANAGEMENT (`wallet-auth`)
**Trạng thái:** **ĐÃ HOÀN THÀNH 100% (13/13 APIs - Live Test Passed)**  
**Base URL qua Gateway:** `/api/v1/auth` hoặc `/api/v1/users`

#### 01. Đăng ký tài khoản mới
- **API:** `POST /api/v1/auth/register`
- **Quyền truy cập:** `PUBLIC`
- **Mô tả:** Đăng ký tài khoản ví mới bằng số điện thoại, mật khẩu và họ tên. Hệ thống tự động tạo ví cá nhân `USER_WALLET` và gửi sự kiện chào mừng qua Kafka.

#### 02. Đăng nhập hệ thống
- **API:** `POST /api/v1/auth/login`
- **Quyền truy cập:** `PUBLIC`
- **Mô tả:** Xác thực số điện thoại và mật khẩu, trả về Access Token (chứa Claim Role) và Refresh Token.

#### 03. Cấp mới Access Token (Token Refresh)
- **API:** `POST /api/v1/auth/refresh`
- **Quyền truy cập:** `PUBLIC`
- **Mô tả:** Sử dụng Refresh Token còn hạn (7 ngày) để cấp lại cặp Access Token & Refresh Token mới (Token Rotation).

#### 04. Đăng xuất & Thu hồi vé (Logout)
- **API:** `POST /api/v1/auth/logout`
- **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Đăng xuất khỏi hệ thống. Access Token bị tống vào Redis Blacklist.

#### 05. Xem thông tin cá nhân của chính mình
- **API:** `GET /api/v1/auth/me`
- **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Lấy thông tin hồ sơ của người dùng đang đăng nhập.

#### 06. Đổi mật khẩu cá nhân
- **API:** `PUT /api/v1/auth/password`
- **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Khách hàng hoặc Admin đổi mật khẩu đăng nhập của chính mình.

#### 07. Nộp hồ sơ định danh eKYC
- **API:** `POST /api/v1/auth/kyc`
- **Quyền truy cập:** `ROLE_USER`
- **Mô tả:** Khách hàng gửi thông tin định danh (CCCD mặt trước/sau). Cho phép nộp lại khi bị `REJECTED`.

#### 08. Xem danh sách người dùng (Quản trị)
- **API:** `GET /api/v1/users`
- **Quyền truy cập:** `ROLE_ADMIN`
- **Mô tả:** Lấy danh sách toàn bộ người dùng có phân trang và bộ lọc.

#### 09. Xem chi tiết người dùng
- **API:** `GET /api/v1/users/{userId}`
- **Quyền truy cập:** `ROLE_ADMIN` hoặc Chính chủ (`userId == callerId`)
- **Mô tả:** Xem chi tiết một người dùng theo UUID (ABAC).

#### 10. Khóa / Mở khóa tài khoản người dùng
- **API:** `PUT /api/v1/users/{userId}/status`
- **Quyền truy cập:** `ROLE_ADMIN`
- **Mô tả:** Khóa (`BLOCKED`) hoặc Mở khóa (`ACTIVE`) tài khoản (chặn tự khóa, chặn khóa Admin).

#### 11. Cập nhật vai trò người dùng (Role Management)
- **API:** `PUT /api/v1/users/{userId}/role`
- **Quyền truy cập:** `ROLE_ADMIN`
- **Mô tả:** Phân quyền vai trò người dùng (chặn tự hạ quyền, chặn sửa Admin, khống chế tối đa 3 Admin).

#### 12. Phê duyệt hoặc Từ chối hồ sơ eKYC
- **API:** `PUT /api/v1/users/{userId}/kyc`
- **Quyền truy cập:** `ROLE_ADMIN`
- **Mô tả:** Quản trị viên thẩm định hồ sơ eKYC (`VERIFIED` hoặc `REJECTED` kèm lý do).

#### 13. Xóa mềm tài khoản người dùng (Soft Delete User)
- **API:** `DELETE /api/v1/users/{userId}`
- **Quyền truy cập:** `ROLE_ADMIN` hoặc Chính chủ (`userId == callerId`)
- **Mô tả:** Đánh dấu xóa mềm (`is_deleted = true`). Tài khoản bị xóa không thể đăng nhập (`4014`).

---

### PHÂN HỆ 2: WALLET & ACCOUNT MANAGEMENT (`wallet-account`)
**Trạng thái:** **ĐÃ HOÀN THÀNH 100% (7 APIs + Helper API - Live Test Passed)**  
**Base URL qua Gateway:** `/api/v1/accounts`

#### 14. Tra cứu thông tin người nhận bằng Số điện thoại (Search Recipient)
- **API:** `GET /api/v1/accounts/search?phoneNumber=...`
- **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`
- **Mô tả:** Khách hàng nhập SĐT người nhận trên app để tra cứu nhanh Họ tên và Account ID khả dụng trước khi thực hiện chuyển tiền (không lộ số dư).
- **Ràng buộc:** Số điện thoại phải tồn tại trong hệ thống, ví đích phải ở trạng thái `ACTIVE`, chặn tra cứu chính mình (`CANNOT_TRANSFER_TO_SELF`).

#### 15. Xem danh sách toàn bộ ví hệ thống (Admin Accounts Dashboard)
- **API:** `GET /api/v1/accounts`
- **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Bảng quản trị danh sách tài khoản ví toàn hệ thống có phân trang (`page`, `size`), hỗ trợ lọc theo trạng thái ví `status` (`ACTIVE`, `FROZEN`, `CLOSED`) và loại ví `accountType` (`USER_WALLET`, `SYSTEM_MASTER`, v.v.).

#### 16. Xem chi tiết thông tin ví theo Account ID
- **API:** `GET /api/v1/accounts/{id}`
- **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR` hoặc Chính chủ ví (ABAC)
- **Mô tả:** Xem số dư khả dụng thời gian thực tính từ Ledger, loại tiền tệ (VND), loại ví, trạng thái ví và ngày mở. Chặn xem trộm ví người khác (`403 Forbidden`).

#### 17. Xem ví theo User ID và Loại ví
- **API:** `GET /api/v1/accounts/user/{userId}?type=USER_WALLET`
- **Quyền truy cập:** `ROLE_ADMIN` hoặc Chính chủ (`userId == callerId`)
- **Mô tả:** Tra cứu thông tin ví mặc định của người dùng dựa vào User ID. Áp dụng cơ chế bảo mật ABAC theo Header Gateway.

#### 18. Đóng băng / Mở khóa ví nghi vấn gian lận
- **API:** `PUT /api/v1/accounts/{id}/status`
- **Quyền truy cập:** `ROLE_ADMIN`
- **Mô tả:** Đổi trạng thái ví sang `ACTIVE`, `FROZEN` (Đóng băng khẩn cấp). Khi ví ở trạng thái `FROZEN`, mọi giao dịch chuyển tiền/rút tiền đi từ ví này lập tức bị từ chối.

#### 19. Xóa mềm / Đóng ví vĩnh viễn (Soft Delete Account)
- **API:** `DELETE /api/v1/accounts/{id}`
- **Quyền truy cập:** `ROLE_ADMIN` hoặc Chính chủ sở hữu ví (ABAC)
- **Mô tả:** Đóng ví vĩnh viễn (`status = CLOSED`).
- **Ràng buộc:** Bắt buộc số dư khả dụng phải bằng 0 (`balance == 0.00`). Nếu ví còn tiền, hệ thống từ chối đóng ví (`ACCOUNT_CANNOT_BE_CLOSED_WITH_BALANCE` - mã `2008`).

#### 20. Tạo tài khoản ví thủ công
- **API:** `POST /api/v1/accounts`
- **Quyền truy cập:** `ROLE_ADMIN`
- **Mô tả:** Tạo mới tài khoản ví đặc biệt (như Ví Hệ thống `SYSTEM_POOL`, Ví Thu hộ/Chi hộ `MERCHANT_WALLET`).

#### API Bổ trợ: Tra cứu ví của chính tôi (Zero-Parameter Lookup)
- **API:** `GET /api/v1/accounts/me`
- **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Tự động trích xuất `callerId` từ `X-User-Id` header của Gateway để trả về ví `USER_WALLET` của người dùng hiện tại mà không cần điền UUID thủ công.

---

### PHÂN HỆ 3: TRANSACTION PROCESSING ENGINE (`wallet-transaction`)
**Trạng thái:** **ĐÃ HOÀN THÀNH 100% (5 APIs - Live Test Passed)**  
**Base URL qua Gateway:** `/api/v1/transfers`, `/api/v1/withdrawals`, `/api/v1/transactions`

#### 21. Chuyển tiền P2P giữa hai ví người dùng
- **API:** `POST /api/v1/transfers`
- **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`
- **Headers bắt buộc:** `X-Signature` (HMAC-SHA256), `X-Idempotency-Key` (UUID), `X-Timestamp`
- **Mô tả:** Chuyển tiền tức thì giữa 2 ví `USER_WALLET`. Áp dụng Lock Ordering chống Deadlock, kiểm tra số dư qua gRPC, ghi nhận Kế toán kép và phát sự kiện Outbox.

#### 22. Rút tiền từ ví về tài khoản ngân hàng
- **API:** `POST /api/v1/withdrawals`
- **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`
- **Headers bắt buộc:** `X-Signature` (HMAC-SHA256), `X-Idempotency-Key` (UUID), `X-Timestamp`
- **Mô tả:** Rút tiền về tài khoản ngân hàng đích, ghi nợ ví người dùng và ghi có ví `BANK_CLEARING`.

#### 23. Tra cứu lịch sử giao dịch của một tài khoản ví cá nhân
- **API:** `GET /api/v1/transactions/history/{accountId}?page=0&size=10`
- **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR` hoặc Chính chủ ví (ABAC)
- **Mô tả:** Danh sách các giao dịch liên quan đến ví (cả chiều gửi và chiều nhận) có phân trang.

#### 24. Tra cứu toàn bộ giao dịch trên toàn hệ thống (Admin Portal)
- **API:** `GET /api/v1/transactions`
- **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Quản trị viên và kiểm toán viên xem toàn bộ giao dịch của mọi ví trong hệ thống.

#### 25. Tra cứu chi tiết một giao dịch
- **API:** `GET /api/v1/transactions/{transactionId}`
- **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR` hoặc Bên liên quan (Người gửi / Người nhận)
- **Mô tả:** Xem chi tiết trạng thái, số tiền, mô tả và thời gian của một giao dịch cụ thể.

---

### PHÂN HỆ 4: PAYMENT GATEWAY INTEGRATION (`wallet-payment`)
**Trạng thái:** **ĐÃ HOÀN THÀNH 100% (3 APIs - Live Test Passed)**  
**Base URL qua Gateway:** `/api/v1/payments`

#### 26. Khởi tạo liên kết nạp tiền (VNPay Sandbox)
- **API:** `POST /api/v1/payments/create`
- **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`
- **Mô tả:** Sinh URL thanh toán VNPay Sandbox kèm chữ ký bảo mật HMAC-SHA512. Tự động tìm ví mặc định của người dùng nếu trường `accountId` không được truyền.

#### 27. Xử lý Webhook IPN (Server-to-Server từ VNPay)
- **API:** `GET /api/v1/payments/{provider}/ipn`
- **Quyền truy cập:** `PUBLIC` (Xác thực bằng chữ ký bí mật của VNPay)
- **Mô tả:** Nhận kết quả thanh toán từ VNPay, kiểm tra checksum, gọi gRPC cộng tiền vào ví và ghi nhận đối ứng tài khoản `BANK_CLEARING`.

#### 28. Xử lý Return URL (Chuyển hướng trình duyệt khách hàng)
- **API:** `GET /api/v1/payments/{provider}/return`
- **Quyền truy cập:** `PUBLIC`
- **Mô tả:** Hiển thị thông báo kết quả giao dịch nạp tiền cho khách hàng trên trình duyệt web.

---

### PHÂN HỆ 5: FINANCIAL AUDIT & RECONCILIATION (`wallet-ledger`)
**Trạng thái:** **ĐÃ HOÀN THÀNH 100% (2 APIs - Live Test Passed)**  
**Base URL qua Gateway:** `/api/v1/ledger`

#### 29. Báo cáo Đối soát Sổ cái Kế toán kép (Financial Reconciliation)
- **API:** `GET /api/v1/ledger/reconciliation`
- **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Thuật toán quét toàn bộ bảng `ledger_entries`, tính tổng độ lệch đại số. Hệ thống xác nhận `balanced: true` và `totalDelta = 0.00`. Tự động báo động nếu lệch tiền.

#### 30. Tra cứu danh sách bút toán sổ cái (Ledger Entries Audit)
- **API:** `GET /api/v1/ledger/entries`
- **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Kiểm toán viên xem từng dòng tiền Nợ (Debit) và Có (Credit) có phân trang (`page`, `size`).

---

### PHÂN HỆ 6: FRAUD DETECTION & RISK MANAGEMENT (`wallet-fraud-detection`)
**Trạng thái:** **ĐÃ HOÀN THÀNH 100% (1 API - Live Test Passed)**  
**Base URL qua Gateway:** `/api/v1/fraud/alerts` (hoặc trực tiếp Port 8000 tại `/alerts`)

#### 31. Tra cứu danh sách cảnh báo gian lận thời gian thực
- **API:** `GET /alerts` (hoặc `GET /api/v1/fraud/alerts`)
- **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
- **Mô tả:** Trả về danh sách cảnh báo rủi ro được phát hiện tức thì từ luồng sự kiện Kafka `transaction-events` (Luật giá trị lớn $\ge 50M$ VNĐ và luật tần suất spam $> 5$ giao dịch/phút).
