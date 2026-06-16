package com.LastBite.common.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Dịch vụ gửi email bằng Spring Boot Starter Mail (Gmail SMTP).
 * <p>
 * Tất cả lần gửi đều chạy bất đồng bộ bằng {@link Async} để không chặn HTTP response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Gửi email xác minh OTP bằng template HTML.
     */
    @Async
    public void sendOtpEmail(String toEmail, String fullName, String otpCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("LastBite — Mã xác minh email của bạn: " + otpCode);
            helper.setText(buildOtpHtml(fullName, otpCode), true);
            helper.setFrom("The Last Bite <thelastbite915@gmail.com>");
            mailSender.send(message);
            log.info("Đã gửi email OTP tới {}", toEmail);
        } catch (Exception e) {
            log.error("Gửi email OTP tới {} thất bại: {}", toEmail, e.getMessage(), e);
            // Không throw — người dùng có thể gửi lại OTP qua /auth/resend-otp
        }
    }

    /**
     * Gửi link xác minh email một lần cho luồng đăng ký.
     */
    @Async
    public void sendVerificationLinkEmail(String toEmail, String fullName, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("LastBite — Xác minh email của bạn");
            helper.setText(buildVerificationLinkHtml(fullName, verificationLink), true);
            helper.setFrom("The Last Bite <thelastbite915@gmail.com>");
            mailSender.send(message);
            log.info("Đã gửi email link xác minh tới {}", toEmail);
        } catch (Exception e) {
            log.error("Gửi email link xác minh tới {} thất bại: {}", toEmail, e.getMessage(), e);
        }
    }

    private String buildOtpHtml(String fullName, String otpCode) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f4f4f7;font-family:'Segoe UI',Roboto,Arial,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f7;padding:40px 0">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)">
                    <!-- Header -->
                    <tr>
                      <td style="background:linear-gradient(135deg,#22c55e 0%%,#16a34a 100%%);padding:32px 40px;text-align:center">
                        <h1 style="margin:0;color:#fff;font-size:28px;font-weight:700">🍽️ LastBite</h1>
                        <p style="margin:8px 0 0;color:rgba(255,255,255,0.9);font-size:14px">Giải cứu đồ ăn thừa</p>
                      </td>
                    </tr>
                    <!-- Body -->
                    <tr>
                      <td style="padding:32px 40px">
                        <p style="margin:0 0 16px;color:#333;font-size:16px">Xin chào <strong>%s</strong>,</p>
                        <p style="margin:0 0 24px;color:#555;font-size:15px;line-height:1.6">
                          Cảm ơn bạn đã đăng ký tài khoản LastBite! Vui lòng sử dụng mã OTP bên dưới để xác minh email của bạn:
                        </p>
                        <!-- OTP Box -->
                        <div style="text-align:center;margin:0 0 24px">
                          <div style="display:inline-block;background:#f0fdf4;border:2px solid #22c55e;border-radius:8px;padding:16px 40px;letter-spacing:8px;font-size:32px;font-weight:700;color:#16a34a">
                            %s
                          </div>
                        </div>
                        <p style="margin:0 0 8px;color:#888;font-size:13px;text-align:center">
                          ⏱️ Mã có hiệu lực trong <strong>10 phút</strong>
                        </p>
                        <p style="margin:0 0 24px;color:#888;font-size:13px;text-align:center">
                          Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.
                        </p>
                        <hr style="border:none;border-top:1px solid #eee;margin:24px 0">
                        <p style="margin:0;color:#aaa;font-size:12px;text-align:center">
                          © 2025 LastBite. Tất cả các quyền được bảo lưu.
                        </p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(escape(fullName), escape(otpCode));
    }

    private String buildVerificationLinkHtml(String fullName, String verificationLink) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"></head>
            <body style="margin:0;padding:0;background:#f4f4f7;font-family:'Segoe UI',Roboto,Arial,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f7;padding:40px 0">
                <tr><td align="center">
                  <table width="480" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08)">
                    <tr>
                      <td style="background:#16a34a;padding:32px 40px;text-align:center">
                        <h1 style="margin:0;color:#fff;font-size:28px;font-weight:700">LastBite</h1>
                        <p style="margin:8px 0 0;color:rgba(255,255,255,0.9);font-size:14px">Xác minh email</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px 40px">
                        <p style="margin:0 0 16px;color:#333;font-size:16px">Xin chào <strong>%s</strong>,</p>
                        <p style="margin:0 0 24px;color:#555;font-size:15px;line-height:1.6">
                          Vui lòng bấm nút bên dưới để xác minh email cho tài khoản LastBite của bạn.
                        </p>
                        <div style="text-align:center;margin:0 0 24px">
                          <a href="%s" style="display:inline-block;background:#16a34a;color:#fff;text-decoration:none;border-radius:8px;padding:14px 28px;font-size:15px;font-weight:700">
                            Xác minh email
                          </a>
                        </div>
                        <p style="margin:0 0 8px;color:#888;font-size:13px;text-align:center">
                          Link có hiệu lực trong <strong>24 giờ</strong>.
                        </p>
                        <p style="margin:0;color:#888;font-size:13px;text-align:center">
                          Nếu bạn không tạo tài khoản LastBite, vui lòng bỏ qua email này.
                        </p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(escape(fullName), escape(verificationLink));
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
