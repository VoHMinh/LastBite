package com.LastBite.modules.payment.gateway;

import com.LastBite.modules.payment.config.PaymentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayOsSignatureService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private final PaymentProperties properties;

    public String signCreatePaymentLink(Long orderCode, BigDecimal amount, String description,
                                        String returnUrl, String cancelUrl) {
        String data = "amount=" + amount.longValue()
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        return hmacSha256(data);
    }

    public boolean verifyWebhook(Map<String, Object> data, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return hmacSha256(toSortedQueryString(data)).equalsIgnoreCase(signature);
    }

    public String signPayout(Map<String, Object> payload) {
        return hmacSha256(toSortedQueryString(payload));
    }

    private String toSortedQueryString(Map<String, Object> data) {
        return data.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + normalizeValue(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private String normalizeValue(Object value) {
        if (value == null || "null".equals(value) || "undefined".equals(value)) {
            return "";
        }
        if (value instanceof List<?> list) {
            try {
                return OBJECT_MAPPER.writeValueAsString(list.stream()
                        .map(item -> item instanceof Map<?, ?> map ? new TreeMap<>(map) : item)
                        .toList());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Cannot serialize PayOS signature value", e);
            }
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return String.valueOf(value);
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getPayos().getChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign PayOS payload", e);
        }
    }
}
