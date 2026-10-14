# ĐẶC TẢ KIỂM THỬ MODULE 2: WALLET & ACCOUNT MANAGEMENT (`wallet-account`)
HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (FINTECH WALLET & PAYMENT GATEWAY)

Tài liệu này đặc tả chi tiết 100% các kịch bản kiểm thử (Test Cases) và hướng dẫn triển khai kỹ thuật cho 7 APIs thuộc Phân hệ Quản lý Tài khoản & Ví điện tử (Từ API 14 đến API 20 theo `docs/FUNCTION-LIST.md`).

---

## I. THÔNG TIN HẠ TẦNG & TIÊU CHUẨN GIAO THỨC

- **Service đảm nhiệm:** `wallet-account`
- **Cổng REST trực tiếp (Direct Port):** `http://localhost:8082`
- **Cổng gRPC Server nội bộ:** `9092` (`AccountGrpcController`)
- **Cổng API Gateway (E2E Testing):** `http://localhost:80`
- **Tiền tố định tuyến Gateway (Route Prefix):** `/api/v1/accounts/**`
- **Cơ sở dữ liệu:** PostgreSQL `wallet_account_db` (Port: `15433`), bảng `accounts`
- **Giao tiếp liên dịch vụ (Inter-service Communication):**
  - **gRPC Client gọi `wallet-ledger` (`:9093`):** Truy vấn số dư động thời gian thực (`GetBalanceRequest`) dựa trên Sổ cái kế toán kép (Single Source of Truth).
  - **gRPC Server cung cấp cho `wallet-auth` & `wallet-transaction` (`:9092`):** Cung cấp các RPC `CreateAccount`, `GetAccountWithLock`, `GetAccountById`, `GetAccountByUserIdAndType`.
  - **REST Client gọi `wallet-auth`:** Tra cứu thông tin người dùng (Họ tên, SĐT) phục vụ API tìm kiếm người nhận.

---

## II. BẢNG MÃ LỖI NGHIỆP VỤ (ERROR CODES) CHO MODULE ACCOUNT

| HTTP Status | ErrorCode | Tên Hằng Số (Enum Name) | Ý Nghĩa Chi Tiết |
| :---: | :---: | :--- | :--- |
| **400** | `1000` | `INVALID_INPUT` | Tham số hoặc dữ liệu đầu vào không đúng định dạng Bean Validation. |
| **400** | `1001` | `INVALID_REQUEST_BODY` | Request body rỗng hoặc sai cấu trúc JSON. |
| **404** | `2001` | `ACCOUNT_NOT_FOUND` | Tài khoản ví không tồn tại trong hệ thống. |
| **409** | `2002` | `ACCOUNT_ALREADY_EXISTS` | Người dùng đã sở hữu tài khoản ví thuộc loại này rồi. |
| **403** | `2003` | `ACCOUNT_FROZEN` | Tài khoản ví đang bị đóng băng khẩn cấp do nghi vấn gian lận. |
| **403** | `2004` | `ACCOUNT_CLOSED` | Tài khoản ví đã bị đóng vĩnh viễn. |
| **404** | `2005` | `RECIPIENT_ACCOUNT_NOT_FOUND` | Không tìm thấy ví người nhận hoặc ví người nhận không khả dụng. |
| **400** | `2006` | `CANNOT_TRANSFER_TO_SELF` | Không thể chuyển tiền hoặc tra cứu chính ví của bạn làm người nhận. |
| **400** | `2007` | `INVALID_ACCOUNT_STATUS` | Trạng thái ví cần cập nhật không hợp lệ. |
| **400** | `2008` | `ACCOUNT_CANNOT_BE_CLOSED_WITH_BALANCE` | Không thể đóng ví khi số dư tài khoản vẫn còn tiền (`balance > 0`). |
| **401** | `5001` | `UNAUTHORIZED` | Thiếu Access Token hoặc Token không hợp lệ / hết hạn. |
| **401** | `5002` | `TOKEN_REVOKED` | Token đã bị thu hồi trong Redis Blacklist sau khi Đăng xuất. |
| **403** | `5003` | `FORBIDDEN` | Không đủ quyền hạn RBAC hoặc vi phạm nguyên tắc Chính chủ (ABAC). |

---

## III. ĐẶC TẢ CHI TIẾT 7 APIS & CÁC KỊCH BẢN KIỂM THỬ

---

### API 14: TRA CỨU NGƯỜI NHẬN THEO SỐ ĐIỆN THOẠI (SEARCH RECIPIENT)
* **Endpoint:** `GET /api/v1/accounts/search?phoneNumber={phoneNumber}`
* **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`
* **Mục đích:** Người dùng nhập SĐT người nhận trên ứng dụng trước khi chuyển tiền; hệ thống trả về thông tin định danh của người nhận để người gửi xác nhận đúng người.
* **Nguyên tắc bảo mật:** KHÔNG trả về số dư (`balance`) của người nhận để đảm bảo quyền riêng tư tài chính.

#### Test Case 14.1: Tra cứu người nhận thành công (Happy Path)
* **Request:**
  ```http
  GET http://localhost:80/api/v1/accounts/search?phoneNumber=0987111222
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body Response: Trả về `accountId`, `userId`, `fullName`, `phoneNumber`, `currency: "VND"`, `status: "ACTIVE"`.

#### Test Case 14.2: Tự tìm kiếm chính số điện thoại của mình (Self Search Error)
* **Request:** Người dùng dùng token của mình và tìm kiếm chính số điện thoại của tài khoản đang đăng nhập.
* **Expected Result:**
  * HTTP Status: `400 Bad Request`
  * Body: `code = 2006` (`CANNOT_TRANSFER_TO_SELF`), thông báo: "Không thể chuyển tiền cho chính tài khoản của bạn".

#### Test Case 14.3: Số điện thoại không tồn tại (Not Found)
* **Request:** `GET http://localhost:80/api/v1/accounts/search?phoneNumber=0999999999`
* **Expected Result:**
  * HTTP Status: `404 Not Found`
  * Body: `code = 2005` (`RECIPIENT_ACCOUNT_NOT_FOUND`).

#### Test Case 14.4: Ví người nhận đang bị đóng băng `FROZEN` (Business Error)
* **Pre-condition:** Tài khoản `0987333444` đã bị đóng băng ví (`status = FROZEN`).
* **Request:** `GET http://localhost:80/api/v1/accounts/search?phoneNumber=0987333444`
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 2003` (`ACCOUNT_FROZEN`), thông báo: "Tài khoản ví người nhận đang bị đóng băng".

---

### API 15: XEM DANH SÁCH TOÀN BỘ VÍ HỆ THỐNG (ADMIN PORTAL)
* **Endpoint:** `GET /api/v1/accounts?page=0&size=10&status=ACTIVE&type=USER_WALLET`
* **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
* **Mục đích:** Quản trị viên và kiểm toán viên xem danh sách toàn bộ ví trong hệ thống, phân trang và lọc theo trạng thái/loại ví.

#### Test Case 15.1: Admin xem danh sách ví thành công (Happy Path)
* **Request:**
  ```http
  GET http://localhost:80/api/v1/accounts?page=0&size=10
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, trả về `PaginationResponse` chứa danh sách các ví kèm số dư thời gian thực từ Ledger.

#### Test Case 15.2: User thường cố tình xem danh sách ví toàn hệ thống (RBAC Error)
* **Request:** Dùng `USER_ACCESS_TOKEN` gọi `GET /api/v1/accounts`.
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 5003` (`FORBIDDEN`).

---

### API 16: XEM CHI TIẾT VÍ THEO ACCOUNT ID
* **Endpoint:** `GET /api/v1/accounts/{id}`
* **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR` hoặc Chính chủ sở hữu ví (ABAC)

#### Test Case 16.1: Chính chủ xem ví của mình thành công (Happy Path)
* **Request:**
  ```http
  GET http://localhost:80/api/v1/accounts/{{MY_ACCOUNT_ID}}
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: Trả về đầy đủ `id`, `userId`, `accountType`, `currency`, `balance`, `status`, `createdAt`.

#### Test Case 16.2: Người dùng khác xem trộm ví không phải của mình (ABAC Security Error)
* **Request:** User B dùng token của mình gọi xem Account ID của User A.
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 5003` (`FORBIDDEN`).

---

### API 17: XEM VÍ THEO USER ID VÀ LOẠI VÍ
* **Endpoint:** `GET /api/v1/accounts/user/{userId}?type=USER_WALLET`
* **Quyền truy cập:** `ROLE_ADMIN` hoặc Chính chủ (`userId == callerId`)

#### Test Case 17.1: Chính chủ tải thông tin ví của mình (Happy Path)
* **Request:**
  ```http
  GET http://localhost:80/api/v1/accounts/user/{{MY_USER_ID}}?type=USER_WALLET
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, trả về thông tin ví mặc định.

#### Test Case 17.2: Người dùng khác xem trộm ví qua User ID (ABAC Violation)
* **Request:** User B gọi xem ví của User A qua User ID.
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 5003` (`FORBIDDEN`).

---

### API 18: ĐÓNG BĂNG / MỞ KHÓA VÍ NGHI VẤN GIAN LẬN
* **Endpoint:** `PUT /api/v1/accounts/{id}/status`
* **Quyền truy cập:** `ROLE_ADMIN`

#### Test Case 18.1: Admin đóng băng ví thành công (Happy Path)
* **Request:**
  ```http
  PUT http://localhost:80/api/v1/accounts/{{TARGET_ACCOUNT_ID}}/status
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "status": "FROZEN",
    "reason": "Phát hiện tần suất giao dịch bất thường"
  }
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: Trạng thái ví chuyển thành `status = FROZEN`.

#### Test Case 18.2: User thường cố tình gọi API đóng băng ví (RBAC Error)
* **Request:** Dùng `USER_ACCESS_TOKEN` gọi API 18.
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 5003` (`FORBIDDEN`).

---

### API 19: XÓA MỀM / ĐÓNG VÍ VĨNH VIỄN (SOFT DELETE ACCOUNT)
* **Endpoint:** `DELETE /api/v1/accounts/{id}`
* **Quyền truy cập:** `ROLE_ADMIN` hoặc Chính chủ sở hữu ví (ABAC)
* **Ràng buộc nghiệp vụ Fintech:** Bắt buộc số dư khả dụng trong ví phải bằng 0 (`balance == 0.00`).

#### Test Case 19.1: Đóng ví thất bại do số dư vẫn còn tiền (Business Constraint Error)
* **Pre-condition:** Ví có số dư `balance = 500,000 VND`.
* **Request:**
  ```http
  DELETE http://localhost:80/api/v1/accounts/{{ACCOUNT_WITH_BALANCE_ID}}
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `400 Bad Request`
  * Body: `code = 2008` (`ACCOUNT_CANNOT_BE_CLOSED_WITH_BALANCE`), thông báo: "Không thể đóng tài khoản ví khi số dư vẫn còn tiền. Vui lòng rút hết tiền trước khi đóng ví."

#### Test Case 19.2: Đóng ví thành công khi số dư bằng 0 (Happy Path)
* **Pre-condition:** Ví có số dư `balance = 0.00 VND`.
* **Request:**
  ```http
  DELETE http://localhost:80/api/v1/accounts/{{EMPTY_ACCOUNT_ID}}
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, thông báo: "Đóng tài khoản ví thành công!".
  * Database: Cột `status` của ví chuyển thành `CLOSED`.

#### Test Case 19.3: Người dùng khác xóa trộm ví không phải của mình (ABAC Security Error)
* **Request:** User B dùng token của mình cố tình gọi xóa ví của User A.
* **Expected Result:**
  * HTTP Status: `403 Forbidden`
  * Body: `code = 5003` (`FORBIDDEN`).

---

### API 20: TẠO TÀI KHOẢN VÍ THỦ CÔNG
* **Endpoint:** `POST /api/v1/accounts`
* **Quyền truy cập:** `ROLE_ADMIN`

#### Test Case 20.1: Admin tạo ví thành công (Happy Path)
* **Request:**
  ```http
  POST http://localhost:80/api/v1/accounts
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "userId": "7b1d4239-2a91-4e3a-967a-12e2b34a6c81",
    "accountType": "USER_WALLET",
    "currency": "VND"
  }
  ```
* **Expected Result:**
  * HTTP Status: `201 Created`
  * Body: `success = true`, trả về ví mới với `status = ACTIVE`, số dư ban đầu `balance = 0`.

#### Test Case 20.2: Tạo trùng lặp ví đã tồn tại (Conflict Error)
* **Request:** Gửi lại đúng `userId` và `accountType` đã có ví.
* **Expected Result:**
  * HTTP Status: `409 Conflict`
  * Body: `code = 2002` (`ACCOUNT_ALREADY_EXISTS`).

---

### API BỔ TRỢ: TRA CỨU VÍ CÁ NHÂN CỦA TÔI (ZERO-PARAMETER ME)
* **Endpoint:** `GET /api/v1/accounts/me`
* **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`
* **Mô tả:** Tự động trích xuất `callerId` từ `X-User-Id` header của Gateway để trả về ví `USER_WALLET` của người dùng hiện tại mà không cần điền UUID thủ công.

#### Test Case ME.1: Lấy ví cá nhân thành công (Happy Path)
* **Request:**
  ```http
  GET http://localhost:80/api/v1/accounts/me
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, trả về `accountType = "USER_WALLET"`, `currency = "VND"`, `balance` lấy động thời gian thực từ Ledger gRPC.

