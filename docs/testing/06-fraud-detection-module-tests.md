# ĐẶC TẢ KIỂM THỬ MODULE 6: FRAUD DETECTION & RISK MANAGEMENT (`wallet-fraud-detection`)
HỆ THỐNG VÍ ĐIỆN TỬ & CỔNG THANH TOÁN (FINTECH WALLET & PAYMENT GATEWAY)

Tài liệu này đặc tả chi tiết 100% các kịch bản kiểm thử (Test Cases), phân tích kiến trúc hướng sự kiện (Event-Driven Architecture) qua Apache Kafka, công nghệ xử lý dữ liệu thời gian thực bằng Python FastAPI, các quy tắc đánh giá rủi ro (Risk Evaluation Rules) và API giám sát cảnh báo gian lận cho Phân hệ Phòng chống Gian lận & Quản trị Rủi ro (API 31 theo `docs/FUNCTION-LIST.md`).

---

## I. THÔNG TIN HẠ TẦNG & KIẾN TRÚC XỬ LÝ RỦI RO THỜI GIAN THỰC

- **Service đảm nhiệm:** `wallet-fraud-detection`
- **Công nghệ nền tảng:** Python 3.10+, FastAPI, `aiokafka`, `redis-py`
- **Cổng REST:** `8000` (Direct: `http://localhost:8000`, Gateway: `/api/v1/fraud/**`)
- **Event Bus:** Apache Kafka `fintech_kafka` (Port: `9094`)
  - **Topic lắng nghe:** `transaction-events`
  - **Consumer Group:** `fraud-detection-group`
- **Bộ đệm & Lưu vết cảnh báo:** Redis (Port: `6379`, DB `1`)
  - **Key đếm tần suất:** `fraud:tx_count:{accountId}` (TTL: 60 giây)
  - **Key lưu lịch sử cảnh báo:** `fraud:alerts` (Redis List, lưu tối đa 100 cảnh báo gần nhất)

---

## II. MA TRẬN QUY TẮC ĐÁNH GIÁ RỦI RO (FRAUD DETECTION RULES)

| Mã quy tắc | Tên quy tắc (Rule Name) | Ngưỡng kích hoạt (Threshold) | Ý nghĩa nghiệp vụ trong FinTech |
| :---: | :--- | :---: | :--- |
| **RULE 1** | **`HIGH_VALUE`** | $\text{Amount} \ge 50,000,000 \text{ VND}$ | Giao dịch có giá trị đột biến vượt mức cho phép của ví cá nhân thông thường. Nghi vấn chuyển tiền rửa tiền hoặc tài khoản bị chiếm đoạt. |
| **RULE 2** | **`VELOCITY_SPAM`** | $> 5 \text{ giao dịch / phút}$ | Cùng một ví thực hiện quá 5 giao dịch trong vòng 60 giây. Nghi vấn botnet tấn công từ chối dịch vụ (DoS), quét thẻ hoặc chia nhỏ dòng tiền (Smurfing). |

---

## III. ĐẶC TẢ CHI TIẾT API 31 & CÁC KỊCH BẢN KIỂM THỬ

---

### API 31: TRA CỨU DANH SÁCH CẢNH BÁO GIAN LẬN THỜI GIAN THỰC
* **Endpoint:** `GET /alerts` (hoặc `GET /api/v1/fraud/alerts` qua Gateway)
* **Quyền truy cập:** `ROLE_ADMIN`, `ROLE_AUDITOR`
* **Mô tả:** Trả về danh sách các cảnh báo gian lận vừa được phát hiện từ luồng giao dịch Kafka.

---

### CÁC KỊCH BẢN KIỂM THỬ (TEST CASES)

#### Test Case 31.1: Kiểm tra trạng thái hoạt động của Service (Health Check)
* **Request:** `GET http://localhost:8000/`
* **Expected Result:**
  * HTTP Status: `200 OK`
  * Body: `{"status": "ok", "service": "Dịch vụ phát hiện gian lận đang hoạt động"}`
  * Terminal: Xuất hiện log `[Phát hiện gian lận] Đã kết nối Kafka :9094. Đang giám sát sự kiện giao dịch...`

#### Test Case 31.2: Kích hoạt Quy tắc 1 — Giao dịch giá trị lớn (Rule: HIGH_VALUE)
* **Pre-condition:** Thực hiện 1 giao dịch nạp/chuyển tiền với số tiền $\ge 50,000,000 \text{ VND}$ (hoặc giả lập bắn event Kafka).
* **Expected Result:**
  * Terminal FastAPI xuất hiện log cảnh báo:  
    `[CẢNH BÁO GIAN LẬN] Giao dịch giá trị lớn bất thường: 50,000,000 VNĐ (Vượt ngưỡng 50,000,000 VNĐ)`
  * Gọi `GET http://localhost:8000/alerts`:
    ```json
    {
      "total": 1,
      "alerts": [
        {
          "accountId": "...",
          "amount": 50000000.0,
          "type": "TRANSFER",
          "rule": "HIGH_VALUE",
          "message": "Giao dịch giá trị lớn bất thường: 50,000,000 VNĐ (Vượt ngưỡng 50,000,000 VNĐ)",
          "timestamp": "..."
        }
      ]
    }
    ```

#### Test Case 31.3: Kích hoạt Quy tắc 2 — Spam giao dịch tần suất cao (Rule: VELOCITY_SPAM)
* **Pre-condition:** Thực hiện 6 giao dịch chuyển tiền liên tiếp trong vòng dưới 60 giây trên cùng 1 ví cá nhân.
* **Expected Result:**
  * Ở lần giao dịch thứ 6, Terminal FastAPI xuất hiện log:  
    `[CẢNH BÁO GIAN LẬN] Tài khoản ... spam giao dịch liên tục (6 lần/phút)!`
  * Gọi `GET http://localhost:8000/alerts`: Xuất hiện cảnh báo có `rule: "VELOCITY_SPAM"`.
