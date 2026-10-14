# ĐẶC TẢ KIỂM THỬ MODULE 4: PAYMENT GATEWAY INTEGRATION (`wallet-payment`)
HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (FINTECH WALLET & PAYMENT GATEWAY)

Tài liệu này đặc tả chi tiết 100% các kịch bản kiểm thử (Test Cases), phân tích nghiệp vụ nạp tiền trực tuyến (Online Top-up), thuật toán mã hóa băm chữ ký VNPay SecureHash (HMAC-SHA512), cơ chế xử lý Webhook IPN Server-to-Server bất đồng bộ, đối ứng Sổ cái kế toán kép và tích hợp gRPC nội bộ cho 3 APIs thuộc Phân hệ Cổng thanh toán (Từ API 26 đến API 28 theo `docs/FUNCTION-LIST.md`).

---

## I. THÔNG TIN HẠ TẦNG & THÔNG SỐ CỔNG THANH TOÁN VNPAY SANDBOX

- **Service đảm nhiệm:** `wallet-payment` (Port: `8084`)
- **Cổng API Gateway:** `http://localhost:80`
- **Tiền tố định tuyến Gateway:** `/api/v1/payments/**`
- **Cổng gRPC Client nội bộ:** Gọi sang `wallet-transaction` (Port: `9091`)
- **Cổng thanh toán tích hợp:** VNPay Payment Gateway Sandbox
  - **Môi trường:** Sandbox (`https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`)
  - **Mã Website (vnp_TmnCode):** `30ZS69H0`
  - **Chuỗi bí mật (vnp_HashSecret):** `LFVVFVGZUETSBIQFJGYFCPHHFUSGVBOF`
  - **Thuật toán chữ ký số:** `HmacSHA512`
  - **Đơn vị tiền tệ:** `VND` (VNPay quy định số tiền gửi lên phải nhân `100`, ví dụ `10,000 VND` -> `1000000`)
  - **Thời gian hết hạn đơn hàng (vnp_ExpireDate):** 15 phút

---

## II. DANH MỤC ENUM & BẢNG MÃ LỖI NGHIỆP VỤ

### 1. Danh mục Enum áp dụng trong Module Payment
* **`PaymentProvider` (`wallet-common`):**
  * `VNPAY`: Cổng thanh toán quốc gia VNPay (mặc định hiện tại).
  * `MOMO`: Ví điện tử MoMo (mở rộng).
  * `STRIPE`: Thẻ quốc tế Visa/Mastercard (mở rộng).
* **`TransactionStatus`:** `PENDING` -> `SUCCESS` hoặc `FAILED`.
* **`AccountStatus`:** Chỉ cho phép nạp vào ví `ACTIVE`. Chặn đứng nếu ví `FROZEN` hoặc `CLOSED`.

### 2. Bảng mã lỗi nghiệp vụ (Error Codes)
| HTTP Status | ErrorCode | Tên Hằng Số | Ý Nghĩa Chi Tiết |
| :---: | :---: | :--- | :--- |
| **400** | `1000` | `INVALID_INPUT` | Số tiền nạp không hợp lệ (< 10,000 VND). |
| **400** | `1001` | `INVALID_REQUEST_BODY` | Request body rỗng hoặc sai cấu trúc JSON. |
| **403** | `2003` | `ACCOUNT_FROZEN` | Ví đang bị đóng băng khẩn cấp, không được phép nạp tiền. |
| **403** | `2004` | `ACCOUNT_CLOSED` | Ví đã bị đóng vĩnh viễn. |
| **404** | `2001` | `ACCOUNT_NOT_FOUND` | Tài khoản ví không tồn tại trong hệ thống. |
| **409** | `3002` | `IDEMPOTENCY_KEY_DUPLICATED` | Trùng lặp mã Idempotency Key (giao dịch nạp đã tồn tại). |
| **401** | `5001` | `UNAUTHORIZED` | Thiếu Access Token hoặc Token hết hạn. |
| **403** | `5003` | `FORBIDDEN` | Nạp tiền vào ví của người khác (vi phạm nguyên tắc ABAC). |
| **200 (IPN)** | `97` | `INVALID_CHECKSUM` | Chữ ký số SecureHash từ VNPay không khớp. |
| **200 (IPN)** | `01` | `ORDER_NOT_FOUND` | Không tìm thấy mã giao dịch trong hệ thống. |
| **200 (IPN)** | `02` | `ORDER_ALREADY_CONFIRMED` | Giao dịch đã được cập nhật trước đó (chống lặp IPN). |
| **200 (IPN)** | `00` | `CONFIRM_SUCCESS` | Xác nhận giao dịch nạp tiền thành công. |

---

## III. MA TRẬN DÒNG TIỀN KẾ TOÁN KÉP KHI NẠP TIỀN THÀNH CÔNG

Khi nhận được Webhook IPN báo nạp tiền thành công từ VNPay:
1. Trạng thái giao dịch trong `transactions`: Chuyển từ `PENDING` -> `SUCCESS`.
2. Bút toán ghi sổ cái `ledger_entries`:
   * **Tài khoản `BANK_CLEARING`** (`3ce7a3ed-900f-4524-92ed-84e413472d8e`): `-Amount` (Ghi Có đối ứng nguồn tiền thực ngân hàng chuyển vào).
   * **Tài khoản `USER_WALLET`** (Ví cá nhân khách hàng): `+Amount` (Ghi Nợ tăng số dư khả dụng ví).
   * **Tổng cân bằng kế toán:** `(-Amount) + (+Amount) = 0.00`.
3. Bảng `outbox_events`: Thêm 1 sự kiện `DEPOSIT` trạng thái `PENDING` để phát Kafka notification gửi thông báo biến động số dư.

---

## IV. ĐẶC TẢ CHI TIẾT 3 APIS & CÁC KỊCH BẢN KIỂM THỬ

---

### API 26: KHỞI TẠO LIÊN KẾT NẠP TIỀN QUA VNPAY SANDBOX
* **Endpoint:** `POST /api/v1/payments/create`
* **Quyền truy cập:** `ROLE_USER`, `ROLE_ADMIN`
* **Headers:** `Authorization: Bearer <TOKEN>`

#### Test Case 26.1: Khởi tạo URL nạp tiền thành công (Happy Path)
* **Pre-condition:** Tài khoản ví ở trạng thái `ACTIVE`.
* **Request Body:**
  ```json
  {
    "accountId": "44df6843-443f-4477-a362-515830c0f1e6",
    "amount": 50000.00,
    "idempotencyKey": "vnpay-topup-001",
    "description": "Nap tien vi qua VNPay",
    "provider": "VNPAY"
  }
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `success = true`, trường `data` trả về chuỗi URL thanh toán VNPay Sandbox có chứa `vnp_SecureHash` và `vnp_TxnRef = vnpay-topup-001`.
  * Database: Bảng `transactions` lưu 1 bản ghi mới với `status = PENDING`.

#### Test Case 26.2: Số tiền nạp dưới mức tối thiểu (< 10,000 VND)
* **Request Body:** `"amount": 5000.00`
* **Expected Result:**
  * HTTP Status: `400 Bad Request`
  * Body: Báo lỗi validation "Số tiền nạp tối thiểu là 10.000 VNĐ".

#### Test Case 26.3: Nạp tiền vào ví của người khác (Vi phạm ABAC)
* **Pre-condition:** Token là User Bích nhưng `accountId` là ví của Admin.
* **Expected Result:**
  * HTTP Status: `403 Forbidden` (`code: 5003`).

---

### API 27: XỬ LÝ WEBHOOK IPN TỪ MÁY CHỦ VNPAY (SERVER-TO-SERVER)
* **Endpoint:** `GET /api/v1/payments/VNPAY/ipn?...`
* **Quyền truy cập:** `PUBLIC` (Máy chủ VNPay gọi tự động ngầm)

#### Test Case 27.1: VNPay thông báo nạp tiền thành công (vnp_ResponseCode = 00)
* **Request:** Gọi IPN với chữ ký `vnp_SecureHash` hợp lệ và `vnp_ResponseCode = 00`.
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body trả về cho VNPay: `{"RspCode": "00", "Message": "Confirm Success"}`
  * Database:
    * `transactions`: Chuyển sang `SUCCESS`.
    * `ledger_entries`: Thêm 2 bản ghi cân bằng Kế toán kép.
    * `outbox_events`: Thêm sự kiện Kafka `DEPOSIT`.

#### Test Case 27.2: Chữ ký số SecureHash không hợp lệ (Bị can thiệp giả mạo)
* **Request:** Thay đổi tham số `vnp_Amount` nhưng giữ nguyên `vnp_SecureHash`.
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body trả về cho VNPay: `{"RspCode": "97", "Message": "Invalid Checksum"}`
  * Tiền trong ví không bị cộng.

#### Test Case 27.3: Gọi lại IPN lần thứ 2 cho giao dịch đã hoàn tất (Chống lặp IPN)
* **Request:** Gửi lại request ở Test Case 27.1.
* **Expected Result:**
  * Trả về kết quả an toàn không bị cộng tiền 2 lần.

---

### API 28: XỬ LÝ RETURN URL (ĐIỀU HƯỚNG TRÌNH DUYỆT KHÁCH HÀNG)
* **Endpoint:** `GET /api/v1/payments/VNPAY/return?...`
* **Quyền truy cập:** `PUBLIC` (Trình duyệt người dùng được redirect về sau khi thanh toán)

#### Test Case 28.1: Khách hàng thanh toán thành công
* **Tham số:** Chữ ký hợp lệ, `vnp_ResponseCode = 00`.
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `status: "SUCCESS"`, `message: "Thanh toán thành công"`, kèm `transactionId`.

#### Test Case 28.2: Khách hàng hủy thanh toán hoặc nhập sai OTP
* **Tham số:** `vnp_ResponseCode != 00` (ví dụ `24`: Khách hủy giao dịch).
* **Expected Result:**
  * HTTP Status: `400 Bad Request`
  * Body: `status: "FAILED"`, `errorCode: "24"`.
