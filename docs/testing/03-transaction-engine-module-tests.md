# ĐẶC TẢ KIỂM THỬ MODULE 3: TRANSACTION PROCESSING ENGINE (`wallet-transaction`)
HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (FINTECH WALLET & PAYMENT GATEWAY)

Tài liệu này đặc tả chi tiết 100% các kịch bản kiểm thử (Test Cases), phân tích nghiệp vụ kế toán kép, bảo mật chữ ký số HMAC-SHA256, cơ chế phòng vệ Idempotency, thuật toán chống Deadlock và bảo mật ABAC cho 5 APIs thuộc Phân hệ Xử lý Giao dịch Core Engine (Từ API 21 đến API 25 theo `docs/FUNCTION-LIST.md`).

---

## I. THÔNG TIN HẠ TẦNG & GIAO THỨC BẢO MẬT

- **Service đảm nhiệm:** `wallet-transaction`
- **Cổng REST trực tiếp:** `http://localhost:8080` (hoặc cổng cấu hình của Transaction)
- **Cổng gRPC Server nội bộ:** `9091` (`TransactionGrpcController`)
- **Cổng API Gateway (E2E Testing):** `http://localhost:80`
- **Tiền tố định tuyến Gateway:** `/api/v1/transfers/**`, `/api/v1/withdrawals/**`, `/api/v1/transactions/**`
- **Cơ sở dữ liệu:** PostgreSQL `wallet_transaction_db` (Port: `15433`), bảng `transactions` và `outbox_events`
- **Khóa phân tán & Chống trùng lặp:** Redis `fintech_redis` (Port: `6379`)
- **Bảo mật Chữ ký số điện tử (HMAC-SHA256):**
  - Mọi request làm thay đổi tài chính (`/transfers`, `/withdrawals`) bắt buộc có:
    - Header `X-Signature`: Mã băm HMAC-SHA256 của `METHOD + PATH + TIMESTAMP + BODY`.
    - Header `X-Timestamp`: Unix Timestamp thời gian gửi (Chống Replay Attack trong 5 phút).
    - Secret Key: `FintechWalletSuperSecretKeyForHmacSecurity`.
- **Chống giao dịch trùng lặp (Idempotency):**
  - Bắt buộc Header `X-Idempotency-Key` (UUIDv4).
  - Redis Lock giữ khóa trong 120 giây.

---

## II. BẢNG MÃ LỖI NGHIỆP VỤ (ERROR CODES) CHO MODULE TRANSACTION

| HTTP Status | ErrorCode | Tên Hằng Số (Enum Name) | Ý Nghĩa Chi Tiết |
| :---: | :---: | :--- | :--- |
| **400** | `1000` | `INVALID_INPUT` | Dữ liệu đầu vào không hợp lệ (số tiền $\le 0$, ví nguồn trùng ví đích). |
| **400** | `1001` | `INVALID_REQUEST_BODY` | Request body rỗng hoặc sai cấu trúc JSON. |
| **400** | `3001` | `INSUFFICIENT_BALANCE` | Số dư khả dụng trong ví không đủ để thực hiện giao dịch. |
| **409** | `3002` | `IDEMPOTENCY_KEY_DUPLICATED` | Giao dịch đang được xử lý hoặc đã hoàn tất (trùng lặp Idempotency Key). |
| **404** | `3003` | `TRANSACTION_NOT_FOUND` | Không tìm thấy mã giao dịch trong hệ thống. |
| **403** | `2003` | `ACCOUNT_FROZEN` | Tài khoản ví đang bị đóng băng khẩn cấp do nghi vấn gian lận. |
| **403** | `2004` | `ACCOUNT_CLOSED` | Tài khoản ví đã bị đóng vĩnh viễn. |
| **404** | `2001` | `ACCOUNT_NOT_FOUND` | Tài khoản ví không tồn tại trong hệ thống. |
| **401** | `5001` | `UNAUTHORIZED` | Thiếu Access Token hoặc Token hết hạn / sai chữ ký HMAC. |
| **401** | `5002` | `TOKEN_REVOKED` | Token đã bị thu hồi trong Redis Blacklist sau khi Đăng xuất. |
| **403** | `5003` | `FORBIDDEN` | Không đủ quyền hạn RBAC hoặc vi phạm nguyên tắc Chính chủ (ABAC). |

---

## III. MA TRẬN TÁC ĐỘNG CỦA CÁC ENUM LÊN 5 APIS TRANSACTION

### 1. Tác động của `AccountStatus` (`ACTIVE`, `FROZEN`, `CLOSED`)
- **`ACTIVE`:** Cho phép thực hiện chuyển tiền, rút tiền, xem lịch sử bình thường.
- **`FROZEN`:** Chặn đứng giao dịch xuất tiền tại API 21 và API 22 (`2003: ACCOUNT_FROZEN`). Vẫn cho phép chính chủ xem lịch sử giao dịch tại API 23.
- **`CLOSED`:** Chặn đứng toàn bộ giao dịch chuyển/rút tại API 21 và API 22 (`2004: ACCOUNT_CLOSED`).

### 2. Tác động của `UserStatus` (`ACTIVE`, `BLOCKED`, `DELETED`)
- **`BLOCKED` / `DELETED`:** Bị Gateway chặn đứng tại lớp xác thực Token Redis Blacklist (`5002: TOKEN_REVOKED`). Không thể gọi bất kỳ API nào trong 5 APIs.

### 3. Tác động của `AccountType` (`USER_WALLET`, `SYSTEM_MASTER`, `SYSTEM_FEE`, `BANK_CLEARING`)
- **API 21:** Chuyển tiền P2P chỉ cho phép giữa 2 ví `USER_WALLET`.
- **API 22:** Rút tiền ghi nợ `USER_WALLET` và đối ứng ghi có `BANK_CLEARING`.

---

## IV. ĐẶC TẢ CHI TIẾT 5 APIS & CÁC KỊCH BẢN KIỂM THỬ

---

### API 21: CHUYỂN TIỀN P2P GIỮA HAI VÍ NGƯỜI DÙNG
* **Endpoint:** `POST /api/v1/transfers`
* **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`
* **Headers bắt buộc:** `Authorization`, `X-Signature` (HMAC-SHA256), `X-Timestamp`, `X-Idempotency-Key`

#### Test Case 21.1: Chuyển tiền P2P thành công (Happy Path)
* **Pre-condition:** Ví A có số dư $\ge 50,000$ VND, ví B ở trạng thái `ACTIVE`.
* **Request:**
  ```http
  POST http://localhost:80/api/v1/transfers
  Authorization: Bearer {{USER_A_TOKEN}}
  X-Idempotency-Key: e8a784d1-81f1-4c6c-8a21-998877665544
  X-Timestamp: {{TIMESTAMP}}
  X-Signature: {{HMAC_SIGNATURE}}
  Content-Type: application/json

  {
    "fromAccountId": "44df6843-443f-4477-a362-515830c0f1e6",
    "toAccountId": "2f1b118c-c82e-43e7-8241-fcae3fa1b4a9",
    "amount": 20000.00,
    "description": "Chuyen tien an trua"
  }
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, trả về `transactionId`, `status = SUCCESS`.
  * Cơ sở dữ liệu:
    - Bảng `transactions`: Thêm 1 dòng trạng thái `SUCCESS`.
    - Bảng `ledger_entries` (Sổ cái): Thêm 2 dòng bút toán đối ứng: Ví A `-20000.00` và Ví B `+20000.00` (Tổng delta = `0.00`).
    - Bảng `outbox_events`: Thêm 1 sự kiện `PENDING` phục vụ phát Kafka notification.

#### Test Case 21.2: Chuyển tiền khi số dư không đủ (Insufficient Balance Error)
* **Pre-condition:** Ví A có số dư `10,000 VND`, chuyển đi `500,000 VND`.
* **Expected Result:**
  * HTTP Status: `400 Bad Request`
  * Body: `code = 3001` (`INSUFFICIENT_BALANCE`), "Số dư tài khoản không đủ".

#### Test Case 21.3: Trùng lặp Idempotency Key (Idempotency Protection Error)
* **Request:** Gửi lại đúng `X-Idempotency-Key` đã giao dịch thành công ở Test Case 21.1.
* **Expected Result:**
  * HTTP Status: `409 Conflict`
  * Body: `code = 3002` (`IDEMPOTENCY_KEY_DUPLICATED`).

#### Test Case 21.4: Ví nguồn đang bị đóng băng `FROZEN` (Business Constraint Error)
* **Pre-condition:** Ví A có `status = FROZEN`.
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 2003` (`ACCOUNT_FROZEN`).

#### Test Case 21.5: Thiếu hoặc sai chữ ký số HMAC-SHA256 (Security Error)
* **Request:** Gửi request không có Header `X-Signature` hoặc thay đổi Body sau khi ký.
* **Expected Result:**
  * HTTP Status: `401 Unauthorized` hoặc `403 Forbidden` tại Gateway.

#### Test Case 21.6: Chuyển tiền cho chính ví của mình (Self-Transfer Error)
* **Request:** `fromAccountId` trùng với `toAccountId`.
* **Expected Result:**
  * HTTP Status: `400 Bad Request`
  * Body: `code = 1000` (`INVALID_INPUT`).

---

### API 22: RÚT TIỀN TỪ VÍ VỀ TÀI KHOẢN NGÂN HÀNG
* **Endpoint:** `POST /api/v1/withdrawals`
* **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`
* **Headers bắt buộc:** `Authorization`, `X-Signature` (HMAC-SHA256), `X-Timestamp`, `X-Idempotency-Key`

#### Test Case 22.1: Rút tiền thành công (Happy Path)
* **Request:**
  ```http
  POST http://localhost:80/api/v1/withdrawals
  Authorization: Bearer {{USER_A_TOKEN}}
  X-Idempotency-Key: f9b885e2-92f2-4d7d-9b32-112233445566
  X-Timestamp: {{TIMESTAMP}}
  X-Signature: {{HMAC_SIGNATURE}}
  Content-Type: application/json

  {
    "accountId": "44df6843-443f-4477-a362-515830c0f1e6",
    "amount": 50000.00,
    "bankCode": "NCB",
    "bankAccountNumber": "9704198526191432198",
    "description": "Rut tien ve the ngan hang"
  }
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, trả về `transactionId`, `status = SUCCESS`.
  * Sổ cái `ledger_entries`: Ví A `-50000.00`, Ví `BANK_CLEARING` `+50000.00`.

#### Test Case 22.2: Rút số tiền vượt quá số dư (Insufficient Balance Error)
* **Expected Result:**
  * HTTP Status: `400 Bad Request`
  * Body: `code = 3001` (`INSUFFICIENT_BALANCE`).

---

### API 23: TRA CỨU LỊCH SỬ GIAO DỊCH VÍ CÁ NHÂN
* **Endpoint:** `GET /api/v1/transactions/history/{accountId}?page=0&size=10`
* **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR` hoặc Chính chủ sở hữu ví (ABAC)

#### Test Case 23.1: Chính chủ xem lịch sử ví của mình (Happy Path)
* **Request:** Dùng Token của chủ ví A gọi lịch sử ví A.
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, trả về `PaginationResponse<TransactionHistoryResponse>` có phân trang, số tiền âm (bị trừ) và số tiền dương (được cộng).

#### Test Case 23.2: Người dùng khác xem trộm lịch sử ví (ABAC Security Error)
* **Request:** User B dùng token của mình gọi xem lịch sử ví A.
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 5003` (`FORBIDDEN`).

---

### API 24: QUẢN TRỊ TRA CỨU TOÀN BỘ GIAO DỊCH HỆ THỐNG (ADMIN DASHBOARD)
* **Endpoint:** `GET /api/v1/transactions?status=SUCCESS&page=0&size=10`
* **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`

#### Test Case 24.1: Admin xem toàn bộ danh sách giao dịch thành công (Happy Path)
* **Request:** Dùng `ADMIN_ACCESS_TOKEN` gọi `GET /api/v1/transactions`.
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, danh sách phân trang các giao dịch toàn hệ thống.

#### Test Case 24.2: User thường cố tình truy cập danh sách giao dịch sàn (RBAC Error)
* **Request:** Dùng `USER_ACCESS_TOKEN` gọi `GET /api/v1/transactions`.
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 5003` (`FORBIDDEN`).

---

### API 25: TRA CỨU CHI TIẾT MỘT GIAO DỊCH TÀI CHÍNH
* **Endpoint:** `GET /api/v1/transactions/{id}`
* **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR` hoặc Bên liên quan giao dịch (ABAC)

#### Test Case 25.1: Xem chi tiết giao dịch thành công (Happy Path)
* **Request:**
  ```http
  GET http://localhost:80/api/v1/transactions/{{TRANSACTION_ID}}
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: Trả về đầy đủ `id`, `accountId`, `amount`, `idempotencyKey`, `description`, `status`, `createdAt`.

#### Test Case 25.2: Mã giao dịch không tồn tại (Not Found Error)
* **Request:** `GET http://localhost:80/api/v1/transactions/00000000-0000-0000-0000-000000000000`
* **Expected Result:**
  * HTTP Status: `404 Not Found`
  * Body: `code = 3003` (`TRANSACTION_NOT_FOUND`).
