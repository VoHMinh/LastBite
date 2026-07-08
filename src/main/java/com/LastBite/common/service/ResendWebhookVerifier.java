package com.LastBite.common.service;

import com.LastBite.common.config.MailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ResendWebhookVerifier {

    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    private final MailProperties mailProperties;

    public boolean verify(String payload, HttpHeaders headers) {
        String secret = mailProperties.getResend().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return !mailProperties.getResend().isRequireWebhookSignature();
        }

        String id = headers.getFirst("svix-id");
        String timestamp = headers.getFirst("svix-timestamp");
        String signature = headers.getFirst("svix-signature");
        if (isBlank(id) || isBlank(timestamp) || isBlank(signature)) {
            return false;
        }
        if (!timestampLooksFresh(timestamp)) {
            return false;
        }

        try {
            String signedContent = id + "." + timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(decodeSecret(secret), "HmacSHA256"));
            String expected = Base64.getEncoder().encodeToString(
                    mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));

            for (String candidate : signature.split(" ")) {
                String value = candidate.startsWith("v1,") ? candidate.substring(3) : candidate;
                if (MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        value.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] decodeSecret(String configuredSecret) {
        String secret = configuredSecret.trim();
        if (secret.startsWith("whsec_")) {
            secret = secret.substring("whsec_".length());
        }
        return Base64.getDecoder().decode(secret);
    }

    private boolean timestampLooksFresh(String timestamp) {
        try {
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            return Math.abs(now - seconds) <= TIMESTAMP_TOLERANCE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
