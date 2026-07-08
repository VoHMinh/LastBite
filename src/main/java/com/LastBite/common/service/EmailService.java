package com.LastBite.common.service;

import com.LastBite.common.config.MailProperties;
import com.LastBite.common.email.EmailDeliveryLog;
import com.LastBite.common.email.EmailDeliveryLogRepository;
import com.LastBite.common.email.EmailDeliveryStatus;
import com.LastBite.common.email.EmailMessageType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final EmailDeliveryLogRepository deliveryLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void sendOtpEmail(String toEmail, String fullName, String otpCode) {
        send(EmailMessageType.OTP,
                toEmail,
                "Ma xac minh LastBite",
                buildOtpText(fullName, otpCode),
                buildOtpHtml(fullName, otpCode));
    }

    @Async
    public void sendVerificationLinkEmail(String toEmail, String fullName, String verificationLink) {
        send(EmailMessageType.VERIFICATION_LINK,
                toEmail,
                "Xac minh email LastBite",
                buildVerificationLinkText(fullName, verificationLink),
                buildVerificationLinkHtml(fullName, verificationLink));
    }

    @Async
    public void sendAccountDeletionVerificationEmail(String toEmail, String fullName, String verificationLink) {
        send(EmailMessageType.ACCOUNT_DELETION_VERIFICATION,
                toEmail,
                "Xac minh yeu cau xoa tai khoan LastBite",
                buildAccountDeletionVerificationText(fullName, verificationLink),
                buildAccountDeletionVerificationHtml(fullName, verificationLink));
    }

    @Async
    public void sendAccountDeletionScheduledEmail(
            String toEmail,
            String fullName,
            String cancellationLink,
            Instant scheduledDeletionAt) {
        send(EmailMessageType.ACCOUNT_DELETION_SCHEDULED,
                toEmail,
                "Tai khoan LastBite da duoc len lich xoa",
                buildAccountDeletionScheduledText(fullName, cancellationLink, scheduledDeletionAt),
                buildAccountDeletionScheduledHtml(fullName, cancellationLink, scheduledDeletionAt));
    }

    @Async
    public void sendTestEmail(String toEmail) {
        send(EmailMessageType.TEST,
                toEmail,
                "LastBite test email",
                "This is a LastBite delivery test email.",
                baseHtml("LastBite", "This is a LastBite delivery test email.", null, null));
    }

    private void send(EmailMessageType messageType, String toEmail, String subject, String text, String html) {
        String normalizedTo = normalizeEmail(toEmail);
        String idempotencyKey = buildIdempotencyKey(messageType);
        EmailDeliveryLog deliveryLog = deliveryLogRepository.save(EmailDeliveryLog.builder()
                .messageType(messageType)
                .provider(mailProperties.getProvider().name())
                .recipientEmail(normalizedTo)
                .subject(subject)
                .idempotencyKey(idempotencyKey)
                .status(EmailDeliveryStatus.PENDING)
                .attempts(0)
                .build());

        int maxAttempts = mailProperties.normalizedMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String providerMessageId = switch (mailProperties.getProvider()) {
                    case RESEND -> sendViaResend(normalizedTo, subject, text, html, idempotencyKey, messageType);
                    case SMTP -> sendViaSmtp(normalizedTo, subject, text, html, idempotencyKey, messageType);
                };
                deliveryLog.setStatus(EmailDeliveryStatus.SENT);
                deliveryLog.setAttempts(attempt);
                deliveryLog.setProviderMessageId(providerMessageId);
                deliveryLog.setLastError(null);
                deliveryLog.setSentAt(Instant.now());
                deliveryLogRepository.save(deliveryLog);
                log.info("Sent {} email to {} via {}", messageType, normalizedTo, mailProperties.getProvider());
                return;
            } catch (EmailSendException e) {
                recordFailure(deliveryLog, attempt, e.getMessage());
                if (!e.retryable() || attempt == maxAttempts) {
                    log.error("Sending {} email to {} failed after {} attempt(s): {}",
                            messageType, normalizedTo, attempt, e.getMessage());
                    return;
                }
                backoff(attempt);
            } catch (Exception e) {
                recordFailure(deliveryLog, attempt, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Sending {} email to {} failed after {} attempt(s): {}",
                            messageType, normalizedTo, attempt, e.getMessage(), e);
                    return;
                }
                backoff(attempt);
            }
        }
    }

    private String sendViaSmtp(
            String toEmail,
            String subject,
            String text,
            String html,
            String idempotencyKey,
            EmailMessageType messageType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(text, html);
            helper.setFrom(new InternetAddress(
                    mailProperties.getFromAddress(),
                    mailProperties.getFromName(),
                    StandardCharsets.UTF_8.name()));
            if (mailProperties.getReplyTo() != null && !mailProperties.getReplyTo().isBlank()) {
                helper.setReplyTo(mailProperties.getReplyTo().trim());
            }
            helper.setSentDate(Date.from(Instant.now()));
            message.setHeader("X-Entity-Ref-ID", idempotencyKey);
            message.setHeader("X-LastBite-Message-Type", messageType.name());
            message.setHeader("Resend-Idempotency-Key", idempotencyKey);
            mailSender.send(message);
            return message.getMessageID() == null ? idempotencyKey : message.getMessageID();
        } catch (Exception e) {
            throw new EmailSendException("SMTP send failed: " + e.getMessage(), true);
        }
    }

    private String sendViaResend(
            String toEmail,
            String subject,
            String text,
            String html,
            String idempotencyKey,
            EmailMessageType messageType) {
        String apiKey = mailProperties.getResend().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmailSendException("Resend API key is not configured", false);
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", mailProperties.formattedFrom());
            payload.put("to", List.of(toEmail));
            payload.put("subject", subject);
            payload.put("html", html);
            payload.put("text", text);
            payload.put("headers", Map.of(
                    "X-Entity-Ref-ID", idempotencyKey,
                    "X-LastBite-Message-Type", messageType.name()));
            payload.put("tags", List.of(Map.of(
                    "name", "message_type",
                    "value", messageType.name().toLowerCase())));

            HttpRequest request = HttpRequest.newBuilder(resendEmailsUri())
                    .timeout(Duration.ofMillis(mailProperties.normalizedTimeoutMillis()))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(mailProperties.normalizedTimeoutMillis()))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = response.statusCode();
            String body = response.body();
            if (status >= 200 && status < 300) {
                JsonNode json = objectMapper.readTree(body);
                JsonNode id = json.get("id");
                return id == null || id.asText().isBlank() ? idempotencyKey : id.asText();
            }
            boolean retryable = status == 429 || status >= 500;
            throw new EmailSendException("Resend send failed with HTTP " + status + ": " + truncate(body, 500), retryable);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Resend send interrupted", true);
        } catch (EmailSendException e) {
            throw e;
        } catch (Exception e) {
            throw new EmailSendException("Resend send failed: " + e.getMessage(), true);
        }
    }

    private URI resendEmailsUri() {
        String baseUrl = mailProperties.getResend().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.resend.com";
        }
        return URI.create(baseUrl.replaceAll("/+$", "") + "/emails");
    }

    private void recordFailure(EmailDeliveryLog deliveryLog, int attempt, String message) {
        deliveryLog.setStatus(EmailDeliveryStatus.FAILED);
        deliveryLog.setAttempts(attempt);
        deliveryLog.setLastError(truncate(message, 1000));
        deliveryLogRepository.save(deliveryLog);
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(1000L, 250L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildIdempotencyKey(EmailMessageType messageType) {
        return "lastbite-" + messageType.name().toLowerCase().replace('_', '-') + "-" + UUID.randomUUID();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String buildOtpText(String fullName, String otpCode) {
        return """
                Xin chao %s,

                Ma xac minh email LastBite cua ban la: %s

                Ma co hieu luc trong 10 phut. Neu ban khong yeu cau ma nay, vui long bo qua email nay.
                """.formatted(plainName(fullName), otpCode);
    }

    private String buildOtpHtml(String fullName, String otpCode) {
        return baseHtml(
                "Ma xac minh email",
                """
                Xin chao <strong>%s</strong>,<br><br>
                Cam on ban da dang ky LastBite. Hay dung ma ben duoi de xac minh email cua ban.
                <div style="font-size:32px;letter-spacing:6px;font-weight:700;color:#14532d;text-align:center;margin:24px 0">%s</div>
                Ma co hieu luc trong <strong>10 phut</strong>. Neu ban khong yeu cau ma nay, vui long bo qua email nay.
                """.formatted(escape(fullName), escape(otpCode)),
                null,
                null);
    }

    private String buildVerificationLinkText(String fullName, String verificationLink) {
        return """
                Xin chao %s,

                Vui long mo lien ket sau de xac minh email LastBite:
                %s

                Link co hieu luc trong 24 gio. Neu ban khong tao tai khoan LastBite, vui long bo qua email nay.
                """.formatted(plainName(fullName), verificationLink);
    }

    private String buildVerificationLinkHtml(String fullName, String verificationLink) {
        return baseHtml(
                "Xac minh email",
                """
                Xin chao <strong>%s</strong>,<br><br>
                Vui long bam nut ben duoi de xac minh email cho tai khoan LastBite cua ban.
                """.formatted(escape(fullName)),
                "Xac minh email",
                verificationLink);
    }

    private String buildAccountDeletionVerificationText(String fullName, String verificationLink) {
        return """
                Xin chao %s,

                Chung toi nhan duoc yeu cau xoa tai khoan LastBite cua ban. Neu dung la ban yeu cau, vui long xac minh tai:
                %s

                Link co hieu luc trong 24 gio. Neu ban khong yeu cau, vui long bo qua email nay.
                """.formatted(plainName(fullName), verificationLink);
    }

    private String buildAccountDeletionVerificationHtml(String fullName, String verificationLink) {
        return baseHtml(
                "Xac minh yeu cau xoa tai khoan",
                """
                Xin chao <strong>%s</strong>,<br><br>
                Chung toi nhan duoc yeu cau xoa tai khoan LastBite cua ban. Neu dung la ban yeu cau, vui long bam nut ben duoi de xac minh.
                """.formatted(escape(fullName)),
                "Xac minh yeu cau",
                verificationLink);
    }

    private String buildAccountDeletionScheduledText(
            String fullName,
            String cancellationLink,
            Instant scheduledDeletionAt) {
        return """
                Xin chao %s,

                Tai khoan LastBite cua ban da duoc len lich xoa vao %s.
                Neu ban doi y, hay huy yeu cau tai:
                %s
                """.formatted(
                plainName(fullName),
                scheduledDeletionAt == null ? "" : scheduledDeletionAt,
                cancellationLink);
    }

    private String buildAccountDeletionScheduledHtml(
            String fullName,
            String cancellationLink,
            Instant scheduledDeletionAt) {
        return baseHtml(
                "Tai khoan da duoc len lich xoa",
                """
                Xin chao <strong>%s</strong>,<br><br>
                Tai khoan LastBite cua ban da duoc len lich xoa vao <strong>%s</strong>.
                Neu ban doi y, hay bam nut ben duoi truoc thoi diem nay de huy yeu cau.
                """.formatted(
                        escape(fullName),
                        escape(scheduledDeletionAt == null ? "" : scheduledDeletionAt.toString())),
                "Huy yeu cau xoa tai khoan",
                cancellationLink);
    }

    private String baseHtml(String title, String bodyHtml, String buttonText, String buttonUrl) {
        String button = "";
        if (buttonText != null && buttonUrl != null && !buttonUrl.isBlank()) {
            button = """
                    <div style="text-align:center;margin:24px 0">
                      <a href="%s" style="display:inline-block;background:#166534;color:#ffffff;text-decoration:none;border-radius:6px;padding:12px 20px;font-size:15px;font-weight:700">%s</a>
                    </div>
                    <p style="margin:16px 0 0;color:#6b7280;font-size:13px;line-height:1.6;word-break:break-word">
                      Neu nut tren khong hoat dong, hay mo lien ket nay: <br>%s
                    </p>
                    """.formatted(escape(buttonUrl), escape(buttonText), escape(buttonUrl));
        }
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background:#f7f7f5;font-family:Arial,sans-serif;color:#1f2937">
                  <table width="100%%" cellpadding="0" cellspacing="0" role="presentation" style="background:#f7f7f5;padding:32px 0">
                    <tr><td align="center">
                      <table width="100%%" cellpadding="0" cellspacing="0" role="presentation" style="max-width:520px;background:#ffffff;border:1px solid #e5e7eb">
                        <tr><td style="padding:24px 28px;border-bottom:1px solid #e5e7eb">
                          <div style="font-size:20px;font-weight:700;color:#14532d">LastBite</div>
                        </td></tr>
                        <tr><td style="padding:28px">
                          <h1 style="margin:0 0 16px;font-size:22px;line-height:1.3;color:#111827">%s</h1>
                          <p style="margin:0;color:#374151;font-size:15px;line-height:1.65">%s</p>
                          %s
                          <p style="margin:24px 0 0;color:#6b7280;font-size:12px;line-height:1.6">
                            Email nay duoc gui tu LastBite de bao ve tai khoan cua ban.
                          </p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escape(title), bodyHtml, button);
    }

    private String plainName(String value) {
        return value == null || value.isBlank() ? "ban" : value.trim();
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static final class EmailSendException extends RuntimeException {
        private final boolean retryable;

        private EmailSendException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        private boolean retryable() {
            return retryable;
        }
    }
}
