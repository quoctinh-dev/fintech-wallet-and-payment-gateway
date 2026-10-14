package com.fintech.wallet.modules.notification.service;

import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class EmailTemplateBuilder {

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("###,### VND");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    /**
     * Mẫu Email 1: Biên lai Biến động số dư (Transaction Receipt)
     */
    public String buildTransactionReceipt(String accountId, String type, double amount) {
        String formattedTime = LocalDateTime.now().format(DATE_FORMATTER);
        String formattedAmount = CURRENCY_FORMAT.format(amount);

        String badgeColor = "#16a34a"; // Xanh lá
        String amountPrefix = "+";

        if ("TRANSFER".equalsIgnoreCase(type) || "WITHDRAW".equalsIgnoreCase(type)) {
            badgeColor = "#dc2626"; // Đỏ
            amountPrefix = "-";
        }

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 20px; }
                    .card { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #e2e8f0; }
                    .header { background: linear-gradient(135deg, #1e3a8a 0%%, #2563eb 100%%); color: #ffffff; padding: 24px; text-align: center; }
                    .header h1 { margin: 0; font-size: 20px; font-weight: 700; letter-spacing: 0.5px; }
                    .body { padding: 28px 24px; }
                    .amount-box { text-align: center; margin-bottom: 24px; padding: 16px; background-color: #f8fafc; border-radius: 8px; border: 1px dashed #cbd5e1; }
                    .amount-title { font-size: 13px; color: #64748b; margin-bottom: 6px; text-transform: uppercase; font-weight: 600; }
                    .amount-value { font-size: 26px; font-weight: 800; color: %s; margin: 0; }
                    .row { display: flex; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f1f5f9; font-size: 14px; }
                    .row-label { color: #64748b; }
                    .row-value { color: #0f172a; font-weight: 600; text-align: right; }
                    .badge { display: inline-block; padding: 4px 10px; border-radius: 9999px; font-size: 12px; font-weight: 700; background-color: #dcfce7; color: #15803d; }
                    .footer { background-color: #f8fafc; padding: 18px 24px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="header">
                        <h1>FINTECH WALLET</h1>
                        <p style="margin: 4px 0 0 0; font-size: 13px; opacity: 0.9;">Thông báo biến động số dư</p>
                    </div>
                    <div class="body">
                        <div class="amount-box">
                            <div class="amount-title">Số tiền giao dịch</div>
                            <div class="amount-value">%s%s</div>
                        </div>
                        <div class="row">
                            <span class="row-label">Tài khoản ví:</span>
                            <span class="row-value">%s</span>
                        </div>
                        <div class="row">
                            <span class="row-label">Loại giao dịch:</span>
                            <span class="row-value">%s</span>
                        </div>
                        <div class="row">
                            <span class="row-label">Thời gian xử lý:</span>
                            <span class="row-value">%s</span>
                        </div>
                        <div class="row" style="border-bottom: none;">
                            <span class="row-label">Trạng thái:</span>
                            <span class="badge">THÀNH CÔNG</span>
                        </div>
                    </div>
                    <div class="footer">
                        <p style="margin: 0 0 4px 0;">Đây là email tự động từ Hệ thống Ví điện tử Fintech.</p>
                        <p style="margin: 0;">Nếu không thực hiện giao dịch này, vui lòng liên hệ ngay tổng đài hỗ trợ.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(badgeColor, amountPrefix, formattedAmount, accountId, type, formattedTime);
    }

    /**
     * Mẫu Email 2: Thư chào mừng mở ví (Welcome Onboarding)
     */
    public String buildWelcomeEmail(String fullName, String phoneNumber) {
        String formattedTime = LocalDateTime.now().format(DATE_FORMATTER);

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 20px; }
                    .card { max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid #e2e8f0; }
                    .header { background: linear-gradient(135deg, #0f172a 0%%, #1e293b 100%%); color: #ffffff; padding: 24px; text-align: center; }
                    .header h1 { margin: 0; font-size: 20px; font-weight: 700; }
                    .body { padding: 28px 24px; color: #334155; line-height: 1.6; }
                    .greeting { font-size: 16px; font-weight: 700; color: #0f172a; margin-bottom: 12px; }
                    .info-box { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px 18px; margin: 18px 0; font-size: 14px; }
                    .footer { background-color: #f8fafc; padding: 18px 24px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="header">
                        <h1>FINTECH WALLET</h1>
                        <p style="margin: 4px 0 0 0; font-size: 13px; opacity: 0.9;">Xác nhận kích hoạt ví thành công</p>
                    </div>
                    <div class="body">
                        <div class="greeting">Xin chào %s,</div>
                        <p>Chúc mừng bạn đã đăng ký tài khoản thành công tại <strong>Fintech Wallet & Payment Gateway</strong>. Ví điện tử của bạn đã sẵn sàng cho các giao dịch nạp, rút và chuyển tiền bảo mật.</p>
                        
                        <div class="info-box">
                            <div><strong>Số điện thoại:</strong> %s</div>
                            <div style="margin-top: 6px;"><strong>Thời gian kích hoạt:</strong> %s</div>
                            <div style="margin-top: 6px;"><strong>Trạng thái bảo mật:</strong> Đã kích hoạt HMAC-SHA256 & 2FA</div>
                        </div>

                        <p style="font-size: 13px; color: #64748b;"><strong>Lưu ý bảo mật:</strong> Không bao giờ chia sẻ mã OTP hoặc Mật khẩu đăng nhập cho bất kỳ ai, kể cả nhân viên hỗ trợ.</p>
                    </div>
                    <div class="footer">
                        <p style="margin: 0;">Fintech Wallet - Nền tảng thanh toán an toàn, tức thì.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(fullName, phoneNumber, formattedTime);
    }
}