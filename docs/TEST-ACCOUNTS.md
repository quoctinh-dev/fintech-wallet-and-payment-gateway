# DANH SÁCH TÀI KHOẢN KIỂM THỬ DOANH NGHIỆP (ENTERPRISE TEST ACCOUNTS)
HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (FINTECH WALLET & PAYMENT GATEWAY)

Tài liệu này chuẩn hóa toàn bộ danh sách tài khoản kiểm thử theo tiêu chuẩn Ngân hàng / Doanh nghiệp Fintech. Bao phủ đầy đủ các kịch bản: **Quản trị viên (Admin)**, **Kiểm toán viên (Auditor)**, **Doanh nghiệp Đối tác (Merchant)**, **Khách hàng VIP (Verified eKYC)**, **Khách hàng Mới**, **Tài khoản Đóng băng (Frozen)**, và **Két sắt Hệ thống (System Master Accounts)**.

---

## I. MA TRẬN TÀI KHOẢN NGƯỜI DÙNG & VÍ CÁ NHÂN (`USER_WALLET`)

> **Mật khẩu dùng chung cho tất cả tài khoản kiểm thử:** `Password@123` (Trừ tài khoản Bích đã đổi mật khẩu sang `NewPassword@456`).

| STT | Phân loại vai trò | Họ và Tên | Số điện thoại | User ID (UUID) | Account ID (Mã ví cá nhân) | Trạng thái User | Trạng thái Ví | eKYC | Mục đích & Kịch bản kiểm thử |
| :---: | :--- | :--- | :---: | :--- | :--- | :---: | :---: | :---: | :--- |
| **01** | **SUPER ADMIN** | **Nguyễn Văn An** | `0987111222` | `5a28ca66-31c2-4ef1-b997-4d8e6569b0e8` | `44df6843-443f-4477-a362-515830c0f1e6` | `ACTIVE` | `ACTIVE` | `UNVERIFIED` | Quản trị viên cấp cao: Duyệt eKYC, Khóa user/ví, Phân quyền Role, Đối soát Kế toán kép. |
| **02** | **ADMIN VẬN HÀNH** | **Lê Hoàng Nam** | `0987222333` | `6b39da77-42d3-4fa2-c008-5e9f7670c1f9` | `55ef7954-554f-4588-b473-626941d1e2f7` | `ACTIVE` | `ACTIVE` | `VERIFIED` | Quản trị viên thứ 2: Dùng để test nghiệp vụ Chống lộng quyền (Admin An không được tự khóa hoặc hạ quyền Admin Nam). |
| **03** | **ADMIN AN NINH** | **Phạm Minh Đức** | `0987333555` | `7c40eb88-53e4-4fb3-d119-6f0a8781d2ea` | `66fa8065-665a-4699-c584-737052e2e3a8` | `ACTIVE` | `ACTIVE` | `VERIFIED` | Quản trị viên thứ 3: Chạm mốc giới hạn tối đa 3 Admin. Dùng để test lỗi `4018: ADMIN_LIMIT_EXCEEDED` khi cố thăng cấp thêm Admin thứ 4. |
| **04** | **AUDITOR** | **Vũ Hải Đăng** | `0987444555` | `8d51fc99-64f5-4fc4-e220-7a1b9892e3fb` | `77ab9176-776b-47aa-d695-848163f3e4b9` | `ACTIVE` | `ACTIVE` | `VERIFIED` | Kiểm toán viên độc lập: Test API 29 (Đối soát Sổ cái Realtime `totalDelta = 0.00`), API 30 (Xem bút toán), API 31 (Giám sát Fraud Alerts). |
| **05** | **USER VIP** | **Trần Thị Bích** | `0987333444` | `e9207688-fb90-49bc-9d9a-bf6a3c6f0d23` | `2f1b118c-c82e-43e7-8241-fcae3fa1b4a9` | `ACTIVE` | `ACTIVE` | `VERIFIED` | Người nhận chuẩn: Đã định danh eKYC. Dùng làm **Người nhận (Receiver)** khi test tìm kiếm SĐT (API 14) hoặc nhận tiền P2P. *(Pass: `NewPassword@456`)*. |
| **06** | **USER MỚI** | **Hoàng Mai Linh** | `0346921795` | `57be392f-274b-41bd-8b3d-8fd0c912641d` | `f57e4912-63f2-4223-a427-c638e68a4035` | `ACTIVE` | `ACTIVE` | `UNVERIFIED` | Khách hàng mới: Dùng để test Nộp hồ sơ eKYC CCCD (API 07), Khởi tạo nạp tiền VNPay Sandbox (API 26), test lỗi 403 ABAC khi xem trộm ví người khác. |
| **07** | **USER CHỜ DUYỆT** | **Đỗ Quang Huy** | `0987666777` | `9e62ad00-75a6-4fd5-f331-8b2ca903f4ac` | `88bc0287-887c-48bb-e706-959274a4e5ca` | `ACTIVE` | `ACTIVE` | `PENDING` | Hồ sơ eKYC đang chờ xử lý: Dùng để Admin test Thẩm định eKYC (`VERIFIED` hoặc `REJECTED` kèm lý do - API 12). |
| **08** | **USER SỐ DƯ 0** | **Nguyễn Thùy Dung** | `0999888777` | `b217c941-31c9-47bc-8990-0833211b4f20` | `fbeecda9-42e2-465f-9276-07306cfc28b4` | `ACTIVE` | `ACTIVE` | `UNVERIFIED` | Khách hàng muốn đóng ví: Số dư bằng 0.00đ. Dùng để test **Xóa mềm / Đóng ví thành công** (`status = CLOSED` - API 19). |
| **09** | **USER ĐÓNG BĂNG** | **Bùi Tuấn Anh** | `0987777888` | `af73be11-86b7-4fe6-a442-9c3db014a5bd` | `99cd1398-998d-49cc-f817-060385b5e6db` | `ACTIVE` | `FROZEN` | `VERIFIED` | Tài khoản nghi vấn rửa tiền: Ví bị đóng băng khẩn cấp. Dùng để test chặn đứng lệnh chuyển/rút tiền (Mã lỗi `2003: ACCOUNT_FROZEN`). |
| **10** | **USER ĐÃ XÓA** | **Đặng Quốc Toàn** | `0911222333` | `74efed23-4c8d-4979-87e6-fc60355882cc` | `5f5b53fc-28f0-4053-b669-6029792be11b` | `DELETED` | `CLOSED` | `UNVERIFIED` | Tài khoản đã bị xóa mềm: Dùng để test chặn đăng nhập (Mã lỗi `4014: ACCOUNT_DELETED`). |

---

## II. KÉT SẮT & VÍ NGHIỆP VỤ HỆ THỐNG (SYSTEM MASTER ACCOUNTS)

Các tài khoản ví này thuộc quyền sở hữu của Sàn Fintech (`userId: 00000000-0000-0000-0000-000000000000`), không thể đăng nhập mà chỉ dùng làm tài khoản đối ứng Kế toán kép:

| Tên Két sắt Nghiệp vụ | Loại ví (`accountType`) | Account ID (UUID) | Tiền tệ | Bản chất nghiệp vụ trong Kế toán kép |
| :--- | :---: | :--- | :---: | :--- |
| **Ví Nguồn Tổng** | `SYSTEM_MASTER` | `58b4792e-427d-437a-b2c5-0f8433fea461` | `VND` | Két sắt nguồn vốn lưu động của sàn ví, dùng để cấp phát hạn mức thanh khoản. |
| **Ví Thanh toán Bù trừ Ngân hàng** | `BANK_CLEARING` | `3ce7a3ed-900f-4524-92ed-84e413472d8e` | `VND` | **Két sắt trung gian ngân hàng:** Ghi nhận đối ứng khi nạp tiền VNPay Sandbox hoặc rút tiền về ngân hàng. Đảm bảo tổng dòng tiền vào ra luôn cân bằng $0.00$. |
| **Ví Thu phí Giao dịch** | `SYSTEM_FEE` | `876a4a0b-261b-4fd4-9e99-68bfa5dffea2` | `VND` | Thu phí xử lý các giao dịch chuyển khoản/rút tiền của người dùng. |
| **Ví Doanh nghiệp Đối tác** | `MERCHANT_WALLET` | `bf84cf22-97c8-4ff7-b553-0d4ec125b6ce` | `VND` | Ví của đối tác thu hộ / chi hộ (Công ty Cổ phần Công nghệ FintechPay Việt Nam). |

---

## III. HƯỚNG DẪN TEST THỰC CHIẾN TRÊN SWAGGER UI TẬP TRUNG (PORT 80)

Truy cập: `http://localhost:80/swagger-ui.html`

### 1. Kịch bản 1: Quản trị viên (Super Admin An)
* **Bước 1:** Chọn Service `1. Authentication & Users (wallet-auth)` trên dropdown.
* **Bước 2:** Gọi `POST /api/v1/auth/login`:
  ```json
  {
    "phoneNumber": "0987111222",
    "password": "Password@123"
  }
  ```
* **Bước 3:** Copy `accessToken` $\rightarrow$ bấm nút **Authorize** (góc trên bên phải) $\rightarrow$ dán token vào.
* **Bước 4:** Thử nghiệm:
  * Phê duyệt eKYC cho Đỗ Quang Huy: `PUT /api/v1/users/9e62ad00-75a6-4fd5-f331-8b2ca903f4ac/kyc` với `{"kycStatus": "VERIFIED"}`.
  * Thử thăng cấp User thành Admin thứ 4 để kiểm tra lỗi chặn `4018: ADMIN_LIMIT_EXCEEDED`.

---

### 2. Kịch bản 2: Khách hàng thường nạp & chuyển tiền (User Mai Linh)
* **Bước 1:** Đăng nhập số điện thoại `0346921795` (Pass: `Password@123`).
* **Bước 2:** Copy `accessToken` $\rightarrow$ Authorize.
* **Bước 3:** Gọi `GET /api/v1/accounts/me` $\rightarrow$ Nhận thông tin ví cá nhân tức thì (Account ID: `f57e4912-63f2-4223-a427-c638e68a4035`).
* **Bước 4:** Khởi tạo nạp tiền VNPay: Gọi `POST /api/v1/payments/create` với số tiền `100000`.
* **Bước 5:** Tra cứu người nhận Trần Thị Bích: Gọi `GET /api/v1/accounts/search?phoneNumber=0987333444` $\rightarrow$ Nhận được tên "Trần Thị Bích" và Account ID của Bích để sẵn sàng chuyển tiền P2P.

---

### 3. Kịch bản 3: Kiểm toán viên đối soát Sổ cái (Auditor Hải Đăng)
* **Bước 1:** Đăng nhập số điện thoại `0987444555` (Pass: `Password@123`).
* **Bước 2:** Copy `accessToken` $\rightarrow$ Authorize.
* **Bước 3:** Chuyển sang service `5. Core Ledger & Reconciliation (wallet-ledger)` trên dropdown.
* **Bước 4:** Gọi `GET /api/v1/ledger/reconciliation` $\rightarrow$ Hệ thống xác nhận `balanced: true` và `totalDelta = 0.00`.
* **Bước 5:** Gọi `GET /api/v1/fraud/alerts` $\rightarrow$ Xem các giao dịch nghi vấn rửa tiền hoặc spam tần suất.
