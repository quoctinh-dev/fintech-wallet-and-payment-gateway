# ĐẶC TẢ KIỂM THỬ MODULE 5: FINANCIAL AUDIT & RECONCILIATION (`wallet-ledger`)
HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (FINTECH WALLET & PAYMENT GATEWAY)

Tài liệu này đặc tả chi tiết 100% các kịch bản kiểm thử (Test Cases), phân tích nguyên lý Kế toán kép bất biến (Append-Only Immutable Ledger), thuật toán đối soát tài chính tự động (Real-time Financial Reconciliation) và tra cứu kiểm toán toàn bộ bút toán Sổ cái cho 2 APIs thuộc Phân hệ Kiểm toán & Sổ cái Lõi (API 29 và API 30 theo `docs/FUNCTION-LIST.md`).

---

## I. THÔNG TIN HẠ TẦNG & NGUYÊN TẮC BẤT BIẾN CỦA SỔ CÁI

- **Service đảm nhiệm:** `wallet-ledger`
- **Cổng REST:** `8083` (Chạy qua Gateway: `http://localhost:80/api/v1/ledger/**`)
- **Cổng gRPC Server nội bộ:** `9093` (`LedgerGrpcService`)
- **Cơ sở dữ liệu:** PostgreSQL `wallet_ledger_db` (Port: `15433`), bảng `ledger_entries`
- **Nguyên lý Kế toán kép (Double-Entry Bookkeeping):**
  - Bảng `ledger_entries` là cơ sở dữ liệu **chỉ thêm mới (Append-Only)** — tuyệt đối không cho phép sửa (`UPDATE`) hay xóa (`DELETE`).
  - Mọi giao dịch tài chính (Chuyển tiền, Nạp tiền, Rút tiền) bắt buộc sinh ra ít nhất 2 dòng bút toán đối ứng:
    $$\sum_{i=1}^{n} \text{Amount}_i = 0.00$$
  - Số dư khả dụng của bất kỳ tài khoản ví nào là tổng lũy kế của tất cả các dòng tiền liên quan:
    $$\text{Balance}(\text{accountId}) = \sum \text{Amount}(\text{entry})$$

---

## II. MA TRẬN PHÂN QUYỀN TRUY CẬP (RBAC)

| Endpoint | Quyền hạn cho phép | Người dùng thường (`ROLE_USER`) | Ý nghĩa nghiệp vụ |
| :--- | :---: | :---: | :--- |
| `GET /api/v1/ledger/reconciliation` | `ROLE_ADMIN`, `ROLE_AUDITOR` | Chặn 403 Forbidden | Xem báo cáo cân bằng Sổ cái toàn sàn (Zero Discrepancy). |
| `GET /api/v1/ledger/entries` | `ROLE_ADMIN`, `ROLE_AUDITOR` | Chặn 403 Forbidden | Kiểm toán viên soi từng dòng tiền Debit/Credit. |

---

## III. ĐẶC TẢ CHI TIẾT 2 APIS & KỊCH BẢN KIỂM THỬ

---

### API 29: BÁO CÁO ĐỐI SOÁT SỔ CÁI TÀI CHÍNH TỰ ĐỘNG
* **Endpoint:** `GET /api/v1/ledger/reconciliation`
* **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
* **Headers:** `Authorization: Bearer <ADMIN_OR_AUDITOR_TOKEN>`

#### Test Case 29.1: Admin thực hiện đối soát Sổ cái cân bằng (Happy Path)
* **Pre-condition:** Sổ cái đã phát sinh giao dịch nạp/chuyển tiền hợp lệ, tổng Nợ = tổng Có.
* **Request:**
  ```http
  GET http://localhost:80/api/v1/ledger/reconciliation
  Authorization: Bearer {{ADMIN_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body:
    ```json
    {
      "success": true,
      "code": 200,
      "message": "Đối soát sổ cái thành công",
      "data": {
        "balanced": true,
        "totalDelta": 0.00,
        "totalEntries": 14,
        "status": "BALANCED_ZERO_ERROR",
        "checkedAt": "..."
      }
    }
    ```
  * Giải thích: `totalDelta = 0.00` chứng minh toàn sàn không bị thất thoát hoặc lệch tiền (Zero Financial Error).

#### Test Case 29.2: User thường gọi API Đối soát (Kiểm tra chặn RBAC 403)
* **Request:** Dùng Token của User Bích (`0987333444`) gọi `GET /api/v1/ledger/reconciliation`.
* **Expected Result:**
  * HTTP Status: `403 Forbidden` (`code: 5003`).

---

### API 30: TRA CỨU DANH SÁCH BÚT TOÁN SỔ CÁI (LEDGER AUDIT LOGS)
* **Endpoint:** `GET /api/v1/ledger/entries?page=0&size=10`
* **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
* **Headers:** `Authorization: Bearer <ADMIN_OR_AUDITOR_TOKEN>`
* **Query Params:**
  * `page`: Trang cần xem (mặc định 0)
  * `size`: Kích thước trang (mặc định 10)
  * `accountId`: (Optional) Lọc theo mã tài khoản ví cụ thể

#### Test Case 30.1: Kiểm toán viên tra cứu toàn bộ bút toán hệ thống (Happy Path)
* **Request:**
  ```http
  GET http://localhost:80/api/v1/ledger/entries?page=0&size=10
  Authorization: Bearer {{ADMIN_TOKEN}}
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body:
    ```json
    {
      "success": true,
      "code": 200,
      "message": "Thao tác thành công",
      "data": {
        "items": [
          {
            "id": "...",
            "transactionId": "...",
            "accountId": "44df6843-443f-4477-a362-515830c0f1e6",
            "amount": -20000.00,
            "createdAt": "..."
          },
          {
            "id": "...",
            "transactionId": "...",
            "accountId": "2f1b118c-c82e-43e7-8241-fcae3fa1b4a9",
            "amount": 20000.00,
            "createdAt": "..."
          }
        ],
        "pagination": {
          "currentPage": 1,
          "pageSize": 10,
          "totalPages": 2,
          "totalEntries": 14
        }
      }
    }
    ```

#### Test Case 30.2: Lọc bút toán theo Account ID cụ thể
* **Request:**
  ```http
  GET http://localhost:80/api/v1/ledger/entries?accountId=44df6843-443f-4477-a362-515830c0f1e6&page=0&size=10
  ```
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: Chỉ chứa các bút toán liên quan tới ví Admin `44df6843...`.

#### Test Case 30.3: User thường cố tình xem bút toán sổ cái (RBAC Chặn 403)
* **Request:** Dùng User Token gọi `GET /api/v1/ledger/entries`.
* **Expected Result:**
  * HTTP Status: `403 Forbidden` (`code: 5003`).
