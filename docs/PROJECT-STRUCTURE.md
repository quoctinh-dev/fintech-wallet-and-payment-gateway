# CẤU TRÚC DỰ ÁN (PROJECT STRUCTURE)

Dự án được thiết kế theo kiến trúc **Polyglot Microservices** hoàn chỉnh kết hợp **Event-Driven Architecture** trên hệ sinh thái Spring Boot 3.3.4, Java 21 và Python FastAPI.

---

## 1. Hệ thống Hạ tầng & Dịch vụ Nền tảng (Infrastructure)

### `wallet-registry` — HTTP: 8761
> **Service Discovery (Netflix Eureka Server):** Danh bạ trung tâm. Các microservice khi khởi động tự động đăng ký tên và cổng với Eureka để hỗ trợ định tuyến động (`discovery:///service-name`).

### `wallet-gateway` — HTTP: 80
> **API Gateway (Spring Cloud Gateway WebFlux Reactive):** Cửa ngõ duy nhất của toàn bộ hệ thống đối với Client bên ngoài và là nơi tập trung Swagger UI cho toàn bộ 6 modules.
- `AuthenticationFilter.java`: Giải mã JWT Token, kiểm tra Sổ đen trên Redis (`jwt:blacklist:<token>`). Chèn `X-User-Id` và `X-User-Roles` vào header chuyển tiếp xuống downstream services.
- `HmacSecurityFilter.java`: Bộ lọc bảo mật cao cấp kiểm tra chữ ký số HMAC-SHA256 (`X-Signature`) và `X-Timestamp` (chống Replay Attack 5 phút) cho các tuyến đường tài chính nhạy cảm (`/transfers`, `/withdrawals`). Tái tạo Request Body an toàn bằng Reactive DataBuffer.
- Tích hợp Aggregated Swagger: Định tuyến tài liệu OpenAPI từ tất cả các Java services và cả Python FastAPI (`http://localhost:8000/openapi.json`).

### PostgreSQL — Cổng: 15433 (Container `fintech_postgres`)
> **Cơ sở dữ liệu quan hệ ACID:** Áp dụng mô hình **Database-per-Service** với 5 cơ sở dữ liệu hoàn toàn độc lập:
1. `wallet_auth_db`: Người dùng (`users`), Phiên làm việc (`refresh_tokens`), Sự kiện Outbox (`outbox_events`).
2. `wallet_account_db`: Tài khoản ví (`accounts` kèm trạng thái `ACTIVE`, `FROZEN`, `CLOSED`).
3. `wallet_transaction_db`: Giao dịch tài chính (`transactions`) và Outbox Events (`outbox_events`).
4. `wallet_ledger_db`: Sổ cái kế toán kép bất biến (`ledger_entries`).
5. `wallet_notification_db`: Nhật ký sự kiện đã tiêu thụ (`audit_logs`).

### Redis — Cổng: 6379 (Container `fintech_redis`)
> **Bộ nhớ In-Memory Key-Value:**
1. **Token Blacklist:** Lưu các token bị thu hồi khi user Đăng xuất (TTL = thời gian còn lại của token).
2. **Idempotency Key:** Khóa chống click đúp, chặn trừ tiền 2 lần cho cùng một giao dịch tài chính (TTL 120s).
3. **Fraud Detection Storage:** Lưu trữ đếm tần suất (`fraud:tx_count:{accountId}`) và danh sách cảnh báo gian lận (`fraud:alerts`).

### Apache Kafka & Zookeeper — Cổng: 9094 & 2181 (Containers `fintech_kafka`, `fintech_zookeeper`)
> **Message Broker:** Hàng đợi thông điệp phân tán phục vụ luồng sự kiện bất đồng bộ (`notification-events`, `transaction-events`).

---

## 2. Danh mục các Microservices Nghiệp vụ (Business Microservices)

### `wallet-common` & `wallet-grpc` [Nền tảng Thư viện chung]
- `wallet-common`: Chứa chuẩn hóa phản hồi `ApiResponse<T>`, tập trung mã lỗi `ErrorCode`, `BusinessException`, các Enum dùng chung (`AccountType`, `AccountStatus`, `UserStatus`, `Role`, `TransactionStatus`, `PaymentProvider`).
- `wallet-grpc`: Định nghĩa Protocol Buffers (`.proto`) và sinh ra các stubs gRPC cho `AccountServiceGrpc`, `LedgerServiceGrpc`, `TransactionServiceGrpc`.

### `wallet-auth` — HTTP: 8081 [13/13 APIs - 100% HOÀN THÀNH]
> **Authentication & User Management:** Quản lý người dùng, đăng ký, đăng nhập, nộp eKYC, phân quyền RBAC/ABAC, khống chế tối đa 3 Admin và xóa mềm.
- `AuthServiceImpl.java`: Cấp phát cặp Access Token & Refresh Token (Token Rotation). Đăng xuất đưa token vào Redis Blacklist. Tích hợp Transactional Outbox phát sự kiện chào mừng sang Kafka.
- `UserServiceImpl.java`: Phân trang người dùng, đổi mật khẩu, nộp eKYC (cho phép nộp lại sau khi bị từ chối), duyệt eKYC kèm lý do, phân quyền (hard-cap 3 Admin), xóa mềm tài khoản (`status = DELETED`), và tra cứu người nhận theo SĐT (`getRecipientByPhone`).

### `wallet-account` — HTTP: 8082 | gRPC: 9092 [7 APIs + /me - 100% HOÀN THÀNH]
> **Account & Wallet Service:** Quản lý thực thể Ví, Số dư, Tra cứu người nhận và Trạng thái ví (`ACTIVE`, `FROZEN`, `CLOSED`).
- `Account.java`: Entity ví chứa cột `status` (`AccountStatus`: `ACTIVE`, `FROZEN`, `CLOSED`).
- `AccountGrpcController.java`: Mở cổng **gRPC Server :9092** cho các microservices khác (`wallet-auth`, `wallet-transaction`, `wallet-payment`) gọi nội bộ.
- `AccountServiceImpl.java`:
  - Gọi gRPC sang `wallet-ledger` (`:9093`) để lấy số dư động thời gian thực (`Single Source of Truth`).
  - Gọi REST nội bộ sang `wallet-auth` để tìm kiếm thông tin người nhận theo SĐT (`searchRecipientByPhone`).
  - Xóa mềm / Đóng ví (`closeAccount`): Ràng buộc bắt buộc số dư ví phải bằng 0 (`balance == 0.00`).
  - Tra cứu ví chính chủ qua `GET /api/v1/accounts/me`.
- Bảo mật ABAC: Trích xuất `X-User-Id` và `X-User-Roles` từ Gateway để chặn xem trộm ví của người khác.

### `wallet-transaction` — HTTP: 8080/8083 | gRPC: 9091 [5 APIs - 100% HOÀN THÀNH]
> **Transaction Processing Engine (Core Engine):** Trái tim của hệ thống thanh toán. Xử lý logic Nạp tiền, Rút tiền và Chuyển khoản P2P.
- `TransactionServiceImpl.java`:
  - Chống giao dịch trùng lặp bằng `X-Idempotency-Key` kết hợp Redis Lock (120s).
  - Sắp xếp thứ tự ID tài khoản (Lock Ordering) để triệt tiêu 100% nguy cơ Deadlock khi chuyển tiền chéo giữa 2 tài khoản.
  - Gọi đồng bộ sang `Account Service` (:9092) để kiểm tra trạng thái và `Ledger Service` (:9093) để ghi bút toán kép.
  - Lưu sự kiện vào bảng `outbox_events` trong cùng một Database Transaction.
- `OutboxEventScheduler.java`: Quét định kỳ đưa sự kiện `PENDING` lên Kafka topic `transaction-events`.

### `wallet-ledger` — HTTP: 8083 | gRPC: 9093 [2 APIs - 100% HOÀN THÀNH]
> **Core Ledger Service (Sổ cái Kế toán Kép):**
- Mở cổng **gRPC Server :9093** cho Transaction Engine gọi ghi sổ.
- Ghi nhận biến động dòng tiền theo nguyên lý Kế toán kép: Mỗi giao dịch có đủ 2 dòng Nợ (Debit) và Có (Credit) đối xứng.
- Bảng `ledger_entries` bất biến (Append-Only), không bao giờ có API `UPDATE` hoặc `DELETE`.
- **Financial Reconciliation Engine:** Cung cấp API `GET /api/v1/ledger/reconciliation` đối soát tính toán cân bằng thời gian thực toàn hệ thống (`totalDelta == 0.00`).

### `wallet-payment` — HTTP: 8084 [3 APIs - 100% HOÀN THÀNH]
> **Payment Gateway Integration:** Cổng thanh toán tích hợp ngân hàng bên thứ ba (VNPay Sandbox).
- Sinh URL nạp tiền VNPay kèm chữ ký kiểm tra tính toàn vẹn (Checksum HMAC-SHA512).
- Tự động gọi gRPC sang `wallet-account` để tìm ví `USER_WALLET` của người dùng đang đăng nhập nếu `accountId` không được truyền.
- Xử lý Webhook IPN Callback an toàn để tự động cộng tiền vào ví và ghi sổ kế toán đối ứng ví `BANK_CLEARING`.

### `wallet-notification` — HTTP: 8085 [Kafka Consumer - 100% HOÀN THÀNH]
> **Dịch vụ Thông báo Bất đồng bộ:**
- Lắng nghe sự kiện từ Kafka (`notification-events`, `transaction-events`).
- Tích hợp Brevo API với bộ HTML Email Templates chuẩn giao diện Ngân hàng (Biên lai giao dịch & Chào mừng mở ví). Lưu nhật ký vào bảng `audit_logs`.

### `wallet-fraud-detection` — HTTP: 8000 [1 API - 100% HOÀN THÀNH]
> **Hệ thống Giám sát Rủi ro Thời gian thực (Python FastAPI):**
- Lắng nghe Kafka phát hiện giao dịch bất thường:
  - Luật 1 (High-Value): Giao dịch $\ge 50,000,000$ VNĐ.
  - Luật 2 (Velocity): Tần suất $> 5$ giao dịch / 1 phút từ cùng một ví.
- Lưu 100 cảnh báo rủi ro gần nhất vào Redis (`fraud:alerts`) và cung cấp endpoint `GET /alerts` (Gateway route: `/api/v1/fraud/alerts`).

---

## 3. Thư mục Tài liệu & Kiểm thử (`docs/`)
- `ROADMAP.md`: Lộ trình phát triển, tiến độ thực tế 100% và Chương trình Master 1 Ngày mổ xẻ code & bảo vệ đồ án.
- `FUNCTION-LIST.md`: Danh sách toàn diện 31 chức năng API và các quy tắc ràng buộc nghiệp vụ.
- `ARCHITECTURE.md`: Tài liệu kiến trúc phân tán Polyglot, mô hình giao tiếp Sync/Async, quy chuẩn bảo mật.
- `AUTHORIZE.md`: Ma trận phân quyền chi tiết cho toàn bộ 31 APIs (RBAC & ABAC).
- `DB.sql`: File khởi tạo Database schema độc lập và dữ liệu hạt giống (Seed Data) cho cả 5 DBs.
- `TEST-ACCOUNTS.md`: Danh sách tài khoản kiểm thử, tài khoản Admin/User, ví hệ thống và hướng dẫn test.
- `RESUME_PROMPT.md`: Prompt tổng hợp toàn diện dùng để tiếp tục công việc bất cứ lúc nào.
- `testing/01-auth-module-tests.md`: Đặc tả kiểm thử chi tiết 13 APIs của Module Auth (Pass 100%).
- `testing/02-account-wallet-module-tests.md`: Đặc tả kiểm thử chi tiết 7 APIs + `/me` của Module Account (Pass 100%).
- `testing/03-transaction-engine-module-tests.md`: Đặc tả kiểm thử chi tiết 5 APIs của Module Transaction (Pass 100%).
- `testing/04-payment-gateway-module-tests.md`: Đặc tả kiểm thử chi tiết 3 APIs của Module Payment VNPay (Pass 100%).
- `testing/05-ledger-reconciliation-module-tests.md`: Đặc tả kiểm thử chi tiết 2 APIs của Module Ledger (Pass 100%).
- `testing/06-fraud-detection-module-tests.md`: Đặc tả kiểm thử chi tiết 1 API của Module Fraud Detection (Pass 100%).
