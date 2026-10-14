# LỜI NHẮC ĐỂ TIẾP TỤC CÔNG VIỆC (RESUME PROMPT)

> **HƯỚNG DẪN:** Khi bạn mở lại phiên làm việc mới (hoặc sau khi khởi động lại máy/ngủ dậy), hãy copy toàn bộ đoạn text trong khung dưới đây và gửi cho AI. AI sẽ nắm bắt chính xác 100% ngữ cảnh, trạng thái hoàn thiện của dự án và tiếp tục dẫn dắt bạn học hiểu code, mổ xẻ từng file từ căn bản nhất theo Lộ trình 1 Ngày để tự tin bảo vệ đồ án tốt nghiệp!

---

```text
Chào bạn, chúng ta đang làm việc trên dự án Fintech Wallet & Payment Gateway (Polyglot Microservices: Spring Boot 3.3.4, Java 21, Python FastAPI, PostgreSQL 15, Redis 7, Kafka :9094, Eureka :8761, Gateway :80).

================================================================================
TÌNH TRẠNG HIỆN TẠI CỦA HỆ THỐNG:
================================================================================
TOÀN BỘ 31/31 APIS CỦA DỰ ÁN ĐÃ ĐƯỢC CODE VÀ KIỂM THỬ LIVE TEST HOÀN TẤT 100%:
1. Module 1 (wallet-auth - Port 8081): 13/13 APIs (JWT, eKYC Re-submit, Token Rotation, Redis Blacklist, Hard-cap 3 Admin, Soft Delete User).
2. Module 2 (wallet-account - Port 8082, gRPC 9092): 7 APIs + GET /api/v1/accounts/me (Số dư realtime từ Ledger, Tìm kiếm người nhận theo SĐT, Đóng băng ví FROZEN, Xóa mềm ví CLOSED ràng buộc balance == 0.00).
3. Module 3 (wallet-transaction - Port 8080/8083, gRPC 9091): 5 APIs (Chuyển tiền P2P, Rút tiền ngân hàng, Lock Ordering chống Deadlock, HMAC-SHA256, Idempotency Redis 120s, Outbox Pattern).
4. Module 4 (wallet-payment - Port 8084): 3 APIs (VNPay Sandbox URL, IPN Server-to-Server, Return Callback, Tự động resolve ví mặc định qua gRPC).
5. Module 5 (wallet-ledger - Port 8083, gRPC 9093): 2 APIs (Kế toán kép Append-Only, Đối soát tài chính realtime totalDelta = 0.00, Tra cứu bút toán kiểm toán).
6. Module 6 (wallet-fraud-detection - Port 8000, Python FastAPI): 1 API (Kafka consumer transaction-events, phát hiện High-Value >= 50M, Velocity > 5 tx/min, lưu Redis, Swagger gom về port 80 tại /api/v1/fraud/alerts).
7. Hạ tầng: Eureka (:8761), Gateway (:80 gom toàn bộ Swagger UI của cả 6 modules), PostgreSQL (:15433 - 5 DBs), Redis (:6379), Kafka (:9094), Notification Service (:8085 Brevo HTML Email).

================================================================================
MỤC TIÊU & NHIỆM VỤ HIỆN TẠI (THEO LỘ TRÌNH 1 NGÀY TRONG docs/ROADMAP.md):
================================================================================
Giai đoạn viết code chức năng ĐÃ KẾT THÚC. Hiện tại chúng ta chuyển trọng tâm 100% sang:
"HỌC VÀ MỔ XẺ TƯỜNG TẬN CODEBASE TỪNG FILE TỪNG MODULE - TỪ CĂN BẢN ĐẾN BẢN CHẤT SÂU XA ĐỂ BẢO VỆ ĐỒ ÁN TỐT NGHIỆP TRONG 1 NGÀY".

Hãy dẫn dắt tôi đi qua từng file theo 4 Phiên học:
- Phiên 1 (Sáng): Nền tảng Core, Hạ tầng & Auth (wallet-common, wallet-grpc, wallet-registry, wallet-gateway, wallet-auth).
- Phiên 2 (Chiều): Core Engine Giao dịch & Kế toán Kép (wallet-account, wallet-ledger, wallet-transaction).
- Phiên 3 (Tối): Cổng thanh toán, Bất đồng bộ & Giám sát Rủi ro (wallet-payment, wallet-notification, wallet-fraud-detection).
- Phiên 4 (Đêm): Kịch bản Live Demo bảo vệ và Luyện 20 câu hỏi vấn đáp Hội đồng phản biện.

================================================================================
QUY TẮC PHẢN HỒI BẮT BUỘC:
================================================================================
1. Đi từng file một, giải thích từ thư viện, annotation, bản chất luồng dữ liệu đến các cơ chế xử lý cốt lõi.
2. Tiếng Việt kỹ thuật, chính xác, súc tích, giải thích bản chất "tại sao lại làm như vậy" thay vì chỉ dịch code.
3. KHÔNG tự ý sửa file code trực tiếp qua tools; nếu cần điều chỉnh gì hãy hiển thị code sạch kèm đường dẫn tuyệt đối để tôi tự thao tác.
4. Mỗi lượt phản hồi tập trung vào 1-2 file trọng điểm để tôi kịp ngấm và phản hồi.

Hãy kiểm tra xem tôi đang dừng lại ở file/module nào trong lộ trình 1 ngày và tiếp tục hướng dẫn tôi ngay!
```
