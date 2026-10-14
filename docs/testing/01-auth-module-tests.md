# ĐẶC TẢ KIỂM THỬ MODULE 1: AUTHENTICATION & USER MANAGEMENT (`wallet-auth`)

HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (FINTECH WALLET & PAYMENT GATEWAY)

Tài liệu này đặc tả chi tiết 100% các kịch bản kiểm thử (Test Cases) cho 13 APIs thuộc Phân hệ Authentication & Quản trị
Người dùng. Mọi kịch bản đều bao phủ đủ 4 nhóm: **Happy Path**, **Validation Error**, **Security / RBAC Error** và **Business Error**.

---

## I. THÔNG TIN HẠ TẦNG & TIÊU CHUẨN GIAO THỨC

- **Service đảm nhiệm:** `wallet-auth`
- **Cổng trực tiếp (Direct Port):** `http://localhost:8081`
- **Cổng API Gateway (Khuyến nghị kiểm thử E2E):** `http://localhost:80`
- **Tiền tố định tuyến Gateway (Route Prefix):** `/api/v1/auth/...` và `/api/v1/users/...`
- **Cơ sở dữ liệu:** PostgreSQL `wallet_auth_db` (Port: `15433`)
- **Bộ nhớ đệm:** Redis `fintech_redis` (Port: `6379`)

---

## II. BẢNG MÃ LỖI NGHIỆP VỤ (ERROR CODES) CHO MODULE AUTH

| HTTP Status | ErrorCode | Tên Hằng Số (Enum Name)       | Ý Nghĩa Chi Tiết                                                                   |
|:-----------:|:---------:|:------------------------------|:-----------------------------------------------------------------------------------|
|   **400**   |  `1000`   | `INVALID_INPUT`               | Tham số hoặc dữ liệu đầu vào không đúng định dạng Bean Validation.                 |
|   **400**   |  `1001`   | `INVALID_REQUEST_BODY`        | Request body rỗng hoặc sai cấu trúc JSON.                                          |
|   **401**   |  `4002`   | `INVALID_CREDENTIALS`         | Sai số điện thoại hoặc mật khẩu đăng nhập.                                         |
|   **404**   |  `4003`   | `USER_NOT_FOUND`              | Không tìm thấy tài khoản người dùng theo ID hoặc SĐT.                              |
|   **403**   |  `4004`   | `ACCOUNT_BLOCKED`             | Tài khoản đã bị quản trị viên khóa, không thể đăng nhập.                           |
|   **401**   |  `4005`   | `INVALID_OLD_PASSWORD`        | Mật khẩu hiện tại không chính xác khi thực hiện đổi mật khẩu.                      |
|   **409**   |  `4006`   | `CITIZEN_ID_ALREADY_EXISTS`   | Số CCCD/Hộ chiếu này đã được định danh cho một tài khoản khác.                     |
|   **409**   |  `4007`   | `KYC_ALREADY_SUBMITTED`       | Hồ sơ eKYC đang ở trạng thái `PENDING` hoặc đã `VERIFIED`.                         |
|   **400**   |  `4008`   | `CANNOT_BLOCK_SELF`           | Quản trị viên không được phép tự khóa tài khoản của chính mình.                    |
|   **400**   |  `4009`   | `CANNOT_BLOCK_ANOTHER_ADMIN`  | Quản trị viên không được phép khóa tài khoản của Admin khác.                       |
|   **400**   |  `4010`   | `CANNOT_DEMOTE_SELF`          | Quản trị viên không được phép tự hạ quyền của chính mình.                          |
|   **400**   |  `4011`   | `CANNOT_MODIFY_ANOTHER_ADMIN` | Quản trị viên không được phép thay đổi Role của Admin khác.                        |
|   **400**   |  `4012`   | `KYC_NOT_IN_PENDING_STATE`    | Không thể phê duyệt/từ chối hồ sơ chưa từng nộp hoặc không ở trạng thái `PENDING`. |
|   **409**   |  `4001`   | `PHONE_NUMBER_ALREADY_EXISTS` | Số điện thoại đã được đăng ký trên hệ thống.                                       |
|   **401**   |  `5001`   | `UNAUTHORIZED`                | Thiếu Access Token hoặc Token không hợp lệ / hết hạn.                              |
|   **401**   |  `5002`   | `TOKEN_REVOKED`               | Token đã bị thu hồi trong Redis Blacklist sau khi Đăng xuất.                       |
|   **403**   |  `5003`   | `FORBIDDEN`                   | Không đủ quyền hạn RBAC hoặc vi phạm nguyên tắc Chính chủ (ABAC).                  |

---

## III. ĐẶC TẢ CHI TIẾT 13 APIS & CÁC KỊCH BẢN KIỂM THỬ

---

### API 01: ĐĂNG KÝ TÀI KHOẢN MỚI

* **Endpoint:** `POST /api/v1/auth/register`
* **Quyền truy cập:** `PUBLIC` (Không yêu cầu Token)

#### Test Case 1.1: Đăng ký thành công (Happy Path)

* **Request:**
  ```http
  POST http://localhost:80/api/v1/auth/register
  Content-Type: application/json

  {
    "phoneNumber": "0987111222",
    "password": "Password@123",
    "fullName": "Nguyễn Văn An"
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK` (hoặc `201 Created`)
    * Body: `code = 200`, `success = true`, trả về `accessToken`, `refreshToken`, `userId`.
    * Database: Bảng `users` sinh bản ghi mới với `status = ACTIVE`, `roles = ROLE_USER`, `kyc_status = UNVERIFIED`.
      Bảng `outbox_events` có event chào mừng.

#### Test Case 1.2: Lỗi Validation định dạng dữ liệu (Validation Error)

* **Request:**
  ```http
  POST http://localhost:80/api/v1/auth/register
  Content-Type: application/json

  {
    "phoneNumber": "12345",
    "password": "123",
    "fullName": ""
  }
  ```
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 1000` (`INVALID_INPUT`), liệt kê chi tiết các trường vi phạm.

#### Test Case 1.3: Số điện thoại đã tồn tại (Business Error)

* **Request:** Gửi lại số điện thoại `0987111222` đã đăng ký ở Test Case 1.1.
* **Expected Result:**
    * HTTP Status: `409 Conflict`
    * Body: `code = 4001` (`PHONE_NUMBER_ALREADY_EXISTS`), thông báo "Số điện thoại đã được đăng ký!".

---

### API 02: ĐĂNG NHẬP HỆ THỐNG

* **Endpoint:** `POST /api/v1/auth/login`
* **Quyền truy cập:** `PUBLIC`

#### Test Case 2.1: Đăng nhập thành công (Happy Path)

* **Request:**
  ```http
  POST http://localhost:80/api/v1/auth/login
  Content-Type: application/json

  {
    "phoneNumber": "0987111222",
    "password": "Password@123"
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Body: Trả về `accessToken`, `refreshToken`, `tokenType = Bearer`, `expiresIn = 86400`.

#### Test Case 2.2: Sai số điện thoại hoặc mật khẩu (Security Error)

* **Request:**
  ```http
  POST http://localhost:80/api/v1/auth/login
  Content-Type: application/json

  {
    "phoneNumber": "0987111222",
    "password": "WrongPassword@999"
  }
  ```
* **Expected Result:**
    * HTTP Status: `401 Unauthorized`
    * Body: `code = 4002` (`INVALID_CREDENTIALS`), thông báo "Số điện thoại hoặc mật khẩu không chính xác!".

#### Test Case 2.3: Tài khoản đã bị khóa (Business / Security Error)

* **Tiền điều kiện:** Tài khoản `0987000000` có `status = BLOCKED`.
* **Request:**
  ```http
  POST http://localhost:80/api/v1/auth/login
  Content-Type: application/json

  {
    "phoneNumber": "0987000000",
    "password": "Password@123"
  }
  ```
* **Expected Result:**
    * HTTP Status: `403 Forbidden`
    * Body: `code = 4004` (`ACCOUNT_BLOCKED`), thông báo "Tài khoản của bạn đã bị khóa bởi Quản trị viên!".

---

### API 03: CẤP LẠI TOKEN (TOKEN REFRESH)

* **Endpoint:** `POST /api/v1/auth/refresh`
* **Quyền truy cập:** `PUBLIC`

#### Test Case 3.1: Refresh Token thành công (Happy Path - Token Rotation)

* **Request:**
  ```http
  POST http://localhost:80/api/v1/auth/refresh
  Content-Type: application/json

  {
    "refreshToken": "{{VALID_REFRESH_TOKEN}}"
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Body: Trả về cặp `accessToken` mới và `refreshToken` mới.
    * Database: Refresh Token cũ bị thu hồi/xóa; Refresh Token mới được ghi nhận.

#### Test Case 3.2: Refresh Token không hợp lệ hoặc đã qua sử dụng (Security Error)

* **Request:** Gửi lại Refresh Token vừa bị thay thế ở Test Case 3.1.
* **Expected Result:**
    * HTTP Status: `401 Unauthorized`
    * Body: `code = 5001` (`UNAUTHORIZED`), thông báo "Refresh Token không hợp lệ hoặc đã hết hạn!".

---

### API 04: ĐĂNG XUẤT HỆ THỐNG (LOGOUT)

* **Endpoint:** `POST /api/v1/auth/logout`
* **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`

#### Test Case 4.1: Đăng xuất thành công & Đưa vào Blacklist (Happy Path)

* **Request:**
  ```http
  POST http://localhost:80/api/v1/auth/logout
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Redis: Token được lưu vào key `jwt:blacklist:<token>` với TTL bằng thời gian sống còn lại của token.
    * Bảng `refresh_tokens`: Bị xóa theo `userId`.

#### Test Case 4.2: Tái sử dụng Token sau khi đăng xuất (Security Error)

* **Request:** Dùng lại chính Token vừa đăng xuất ở Test Case 4.1 để gọi bất kỳ API nào (ví dụ `GET /api/v1/auth/me`).
* **Expected Result:**
    * HTTP Status: `401 Unauthorized`
    * Body: `code = 5002` (`TOKEN_REVOKED`), thông báo "Phiên đăng nhập đã bị hủy!".

---

### API 05: XEM HỒ SƠ CÁ NHÂN

* **Endpoint:** `GET /api/v1/auth/me`
* **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`

#### Test Case 5.1: Lấy thông tin cá nhân chính chủ (Happy Path)

* **Request:**
  ```http
  GET http://localhost:80/api/v1/auth/me
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Body: Trả về thông tin của chính token gọi (`id`, `phoneNumber`, `fullName`, `roles`, `kycStatus`, `status`).

#### Test Case 5.2: Không gửi token (Security Error)

* **Request:** `GET http://localhost:80/api/v1/auth/me` (Không Header Authorization)
* **Expected Result:**
    * HTTP Status: `401 Unauthorized`

---

### API 06: ĐỔI MẬT KHẨU CÁ NHÂN

* **Endpoint:** `PUT /api/v1/auth/password`
* **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`, `ROLE_AUDITOR`

#### Test Case 6.1: Đổi mật khẩu thành công (Happy Path)

* **Request:**
  ```http
  PUT http://localhost:80/api/v1/auth/password
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "oldPassword": "Password@123",
    "newPassword": "NewPassword@456"
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Body: "Đổi mật khẩu thành công!".

#### Test Case 6.2: Sai mật khẩu cũ (Security Error)

* **Request:** Nhập `oldPassword` không khớp với mật khẩu đang lưu.
* **Expected Result:**
    * HTTP Status: `401 Unauthorized`
    * Body: `code = 4005` (`INVALID_OLD_PASSWORD`).

#### Test Case 6.3: Mật khẩu mới trùng mật khẩu cũ hoặc không đủ độ mạnh (Validation Error)

* **Request:**
  ```http
  PUT http://localhost:80/api/v1/auth/password
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "oldPassword": "Password@123",
    "newPassword": "Password@123"
  }
  ```
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 4017` (`NEW_PASSWORD_SAME_AS_OLD`), "Mật khẩu mới không được trùng với mật khẩu cũ".

---

### API 07: NỘP HỒ SƠ ĐỊNH DANH eKYC

* **Endpoint:** `POST /api/v1/auth/kyc/submit`
* **Quyền truy cập:** `ROLE_USER`

#### Test Case 7.1: Nộp hồ sơ eKYC lần đầu thành công (Happy Path)

* **Request:**
  ```http
  POST http://localhost:80/api/v1/auth/kyc/submit
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "citizenId": "012345678901",
    "idCardFrontUrl": "https://storage.fintech.com/kyc/front.jpg",
    "idCardBackUrl": "https://storage.fintech.com/kyc/back.jpg"
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Database: Cập nhật `citizen_id = 012345678901`, `kyc_status = PENDING`.

#### Test Case 7.2: Số CCCD sai định dạng hoặc thiếu link ảnh (Validation Error)

* **Request:** `citizenId = "12345"` (< 12 số) hoặc thiếu `idCardFrontUrl`.
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 1000` (`INVALID_INPUT`).

#### Test Case 7.3: Số CCCD đã được người khác sử dụng (Business Error)

* **Request:** User B nộp CCCD `012345678901` mà User A đã nộp.
* **Expected Result:**
    * HTTP Status: `409 Conflict`
    * Body: `code = 4006` (`CITIZEN_ID_ALREADY_EXISTS`).

#### Test Case 7.4: Nộp lại khi hồ sơ đang chờ duyệt hoặc đã duyệt (Business Error)

* **Request:** User đang có `kyc_status = PENDING` hoặc `VERIFIED` tiếp tục gọi API này.
* **Expected Result:**
    * HTTP Status: `409 Conflict`
    * Body: `code = 4007` (`KYC_ALREADY_SUBMITTED`).

---

### API 08: XEM DANH SÁCH NGƯỜI DÙNG TOÀN HỆ THỐNG

* **Endpoint:** `GET /api/v1/users?page=0&size=10&role=ROLE_USER&kycStatus=PENDING`
* **Quyền truy cập:** `ROLE_ADMIN`

#### Test Case 8.1: Admin xem danh sách thành công có phân trang (Happy Path)

* **Request:**
  ```http
  GET http://localhost:80/api/v1/users?page=0&size=10
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Body: Đóng gói trong `PaginationResponse<UserResponse>`, chứa `content`, `page`, `size`, `totalElements`,
      `totalPages`.

#### Test Case 8.2: User hoặc Auditor cố tình truy cập (Security Error)

* **Request:** Gửi token có role `ROLE_USER` hoặc `ROLE_AUDITOR`.
* **Expected Result:**
    * HTTP Status: `403 Forbidden`
    * Body: `code = 5003` (`FORBIDDEN`).

---

### API 09: XEM CHI TIẾT NGƯỜI DÙNG THEO ID

* **Endpoint:** `GET /api/v1/users/{userId}`
* **Quyền truy cập:** `ROLE_ADMIN` HOẶC Chính chủ (`#p0.toString() == authentication.name`)

#### Test Case 9.1: Admin xem chi tiết bất kỳ người dùng (Happy Path)

* **Request:**
  ```http
  GET http://localhost:80/api/v1/users/{{ANY_USER_ID}}
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Body: Thông tin đầy đủ của User được yêu cầu.

#### Test Case 9.2: Khách hàng xem thông tin của chính mình (Happy Path ABAC)

* **Request:**
  ```http
  GET http://localhost:80/api/v1/users/{{OWN_USER_ID}}
  Authorization: Bearer {{USER_ACCESS_TOKEN}}
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`

#### Test Case 9.3: Khách hàng xem trộm thông tin User khác (Security Error ABAC)

* **Request:** User A gửi UUID của User B.
* **Expected Result:**
    * HTTP Status: `403 Forbidden`
    * Body: `code = 5003` (`FORBIDDEN`).

#### Test Case 9.4: Tìm kiếm UUID không tồn tại (Business Error)

* **Request:** Admin tìm kiếm UUID ngẫu nhiên không có trong database.
* **Expected Result:**
    * HTTP Status: `404 Not Found`
    * Body: `code = 4003` (`USER_NOT_FOUND`).

---

### API 10: KHÓA / MỞ KHÓA TÀI KHOẢN NGƯỜI DÙNG

* **Endpoint:** `PUT /api/v1/users/{userId}/status`
* **Quyền truy cập:** `ROLE_ADMIN`

#### Test Case 10.1: Admin khóa tài khoản User thường thành công (Happy Path)

* **Request:**
  ```http
  PUT http://localhost:80/api/v1/users/{{USER_ID}}/status
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "status": "BLOCKED"
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Database: `status = BLOCKED`. Khi User này login sẽ bị chặn.

#### Test Case 10.2: Chống lộng quyền - Admin tự khóa chính mình (Anti-Privilege Escalation)

* **Request:** Admin truyền `userId` là ID của chính mình trong URL.
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 4008` (`CANNOT_BLOCK_SELF`), "Quản trị viên không thể tự khóa tài khoản của chính mình!".

#### Test Case 10.3: Chống lộng quyền - Admin khóa một Admin khác (Anti-Privilege Escalation)

* **Request:** Admin 1 cố khóa tài khoản của Admin 2.
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 4009` (`CANNOT_BLOCK_ANOTHER_ADMIN`), "Không có quyền khóa tài khoản của Quản trị viên khác!".

---

### API 11: CẬP NHẬT VAI TRÒ NGƯỜI DÙNG (ROLE MANAGEMENT)

* **Endpoint:** `PUT /api/v1/users/{userId}/role`
* **Quyền truy cập:** `ROLE_ADMIN`

#### Test Case 11.1: Admin nâng quyền User thường thành Auditor (Happy Path)

* **Request:**
  ```http
  PUT http://localhost:80/api/v1/users/{{USER_ID}}/role
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "role": "ROLE_AUDITOR"
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Database: `roles = ROLE_AUDITOR`.

#### Test Case 11.2: Chống lộng quyền - Admin tự hạ quyền của chính mình (Anti-Privilege Escalation)

* **Request:** Admin truyền `userId` là ID của chính mình và set `role = ROLE_USER`.
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 4010` (`CANNOT_DEMOTE_SELF`), "Quản trị viên không thể tự hạ quyền của chính mình!".

#### Test Case 11.3: Chống lộng quyền - Admin hạ quyền Admin khác (Anti-Privilege Escalation)

* **Request:** Admin 1 thay đổi role của Admin 2.
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 4011` (`CANNOT_MODIFY_ANOTHER_ADMIN`).

---

### API 12: PHÊ DUYỆT HOẶC TỪ CHỐI HỒ SƠ eKYC

* **Endpoint:** `PUT /api/v1/users/{userId}/kyc`
* **Quyền truy cập:** `ROLE_ADMIN`

#### Test Case 12.1: Admin phê duyệt eKYC thành công (Happy Path - Approved)

* **Tiền điều kiện:** User đang ở trạng thái `kyc_status = PENDING`.
* **Request:**
  ```http
  PUT http://localhost:80/api/v1/users/{{PENDING_USER_ID}}/kyc
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "status": "VERIFIED"
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Database: `kyc_status = VERIFIED`, `kyc_rejection_reason = null`.

#### Test Case 12.2: Admin từ chối eKYC kèm lý do (Happy Path - Rejected)

* **Request:**
  ```http
  PUT http://localhost:80/api/v1/users/{{PENDING_USER_ID}}/kyc
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  Content-Type: application/json

  {
    "status": "REJECTED",
    "rejectionReason": "Hình ảnh CMND/CCCD bị mờ, không rõ số."
  }
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Database: `kyc_status = REJECTED`, `kyc_rejection_reason = "Hình ảnh CMND/CCCD bị mờ, không rõ số."`.

#### Test Case 12.3: Từ chối eKYC nhưng không cung cấp lý do (Validation Error)

* **Request:** Gửi `status = REJECTED` nhưng để trống hoặc thiếu `rejectionReason`.
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 4012` (`KYC_NOT_IN_PENDING_STATE`), "Chỉ có thể phê duyệt hồ sơ đang ở trạng thái PENDING!".

---

### API 13: XÓA MỀM TÀI KHOẢN (SOFT DELETE / ACCOUNT CLOSURE)

* **Endpoint:** `DELETE /api/v1/users/{userId}`
* **Quyền truy cập:** `ROLE_ADMIN` HOẶC Chính chủ (`#p0.toString() == authentication.name`)

#### Test Case 13.1: Admin xóa mềm tài khoản User thường thành công (Happy Path)

* **Request:**
  ```http
  DELETE http://localhost:80/api/v1/users/{{USER_ID}}
  Authorization: Bearer {{ADMIN_ACCESS_TOKEN}}
  ```
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Body: `code = 200`, `success = true`, "Đã xóa mềm và đóng tài khoản thành công!".
    * Database: `status = DELETED`.
    * Redis: Toàn bộ token của User bị thu hồi vào Blacklist.

#### Test Case 13.2: User tự đóng tài khoản của chính mình (Happy Path ABAC)

* **Request:** User gọi xóa ID của chính mình kèm access token của mình.
* **Expected Result:**
    * HTTP Status: `200 OK`
    * Database: `status = DELETED`.

#### Test Case 13.3: User A xóa trộm tài khoản User B (Security Error ABAC)

* **Request:** User A gửi UUID của User B.
* **Expected Result:**
    * HTTP Status: `403 Forbidden`
    * Body: `code = 5003` (`FORBIDDEN`).

#### Test Case 13.4: Chống lộng quyền - Admin tự xóa chính mình (Anti-Privilege Escalation)

* **Request:** Admin gửi URL xóa chính ID của mình.
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 4015` (`CANNOT_DELETE_SELF`), "Quản trị viên không thể tự xóa tài khoản của chính mình!".

#### Test Case 13.5: Chống lộng quyền - Admin xóa Admin khác (Anti-Privilege Escalation)

* **Request:** Admin 1 gọi xóa ID của Admin 2.
* **Expected Result:**
    * HTTP Status: `400 Bad Request`
    * Body: `code = 4016` (`CANNOT_DELETE_ANOTHER_ADMIN`), "Không có quyền xóa tài khoản của Quản trị viên khác!".

#### Test Case 13.6: Đăng nhập sau khi tài khoản đã bị xóa mềm (Business Error)

* **Request:** Gọi `POST /api/v1/auth/login` bằng tài khoản đã có `status = DELETED`.
* **Expected Result:**
    * HTTP Status: `403 Forbidden`
    * Body: `code = 4014` (`ACCOUNT_DELETED`), "Tài khoản của bạn đã bị đóng vĩnh viễn!".

---

## IV. BẢNG TỔNG HỢP KIỂM THỬ (TEST MATRIX SUMMARY)

| Mã API | Tên Endpoint                        | Happy Path | Validation (400) | Security (401/403) |     Business (400/404/409)      |
|:------:|:------------------------------------|:----------:|:----------------:|:------------------:|:-------------------------------:|
| **01** | `POST /api/v1/auth/register`        |  (200)   |     (1000)     |         —          |       (4001 Duplicate)        |
| **02** | `POST /api/v1/auth/login`           |  (200)   |     (1000)     |  (4002 Invalid)  | (4004 Blocked / 4014 Deleted) |
| **03** | `POST /api/v1/auth/refresh`         |  (200)   |     (1000)     |  (5001 Expired)  |                —                |
| **04** | `POST /api/v1/auth/logout`          |  (200)   |        —         |  (5002 Revoked)  |                —                |
| **05** | `GET /api/v1/auth/me`               |  (200)   |        —         |  (5001 Unauth)   |                —                |
| **06** | `PUT /api/v1/auth/password`         |  (200)   |     (1000)     | (4005 Wrong Old) |                —                |
| **07** | `POST /api/v1/auth/kyc/submit`      |  (200)   |     (1000)     |   (5003 Role)    |         (4006 / 4007)         |
| **08** | `GET /api/v1/users`                 |  (200)   |        —         | (5003 Non-Admin) |                —                |
| **09** | `GET /api/v1/users/{userId}`        |  (200)   |        —         |   (5003 ABAC)    |       (4003 Not Found)        |
| **10** | `PUT /api/v1/users/{userId}/status` |  (200)   |        —         | (5003 Non-Admin) |   (4008 Self / 4009 Admin)    |
| **11** | `PUT /api/v1/users/{userId}/role`   |  (200)   |        —         | (5003 Non-Admin) |   (4010 Self / 4011 Admin)    |
| **12** | `PUT /api/v1/users/{userId}/kyc`    |  (200)   | (1000 Reason)  | (5003 Non-Admin) |      (4012 Not Pending)       |
| **13** | `DELETE /api/v1/users/{userId}`     |  (200)   |        —         |   (5003 ABAC)    |   (4015 Self / 4016 Admin)    |
