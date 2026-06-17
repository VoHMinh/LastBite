package com.LastBite.modules.payment.gateway;

import com.LastBite.modules.payment.config.PaymentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payments", name = "gateway", havingValue = "payos")
public class PayOsPayoutGatewayAdapter implements PayoutGatewayPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final PaymentProperties properties;
    private final PayOsSignatureService signatureService;

    @Override
    @SuppressWarnings("unchecked")
    public PayoutResult createPayout(CreatePayoutCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("referenceId", command.referenceId());
        payload.put("amount", command.amount().longValue());
        payload.put("description", command.description());
        payload.put("toBin", command.toBin());
        payload.put("toAccountNumber", command.toAccountNumber());
        payload.put("category", List.of(command.category() == null ? "merchant_settlement" : command.category()));

        Map<String, Object> response = restClient().post()
                .uri("/v1/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-idempotency-key", command.idempotencyKey())
                .header("x-signature", signatureService.signPayout(payload))
                .body(payload)
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = response == null ? Map.of() : (Map<String, Object>) response.getOrDefault("data", Map.of());
        Map<String, Object> transaction = firstTransaction(data.get("transactions"));
        return new PayoutResult(
                stringValue(data.get("id")),
                stringValue(transaction.get("id")),
                stringValue(transaction.getOrDefault("state", data.get("approvalState"))),
                toJson(response)
        );
    }

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(properties.getPayos().getBaseUrl())
                .defaultHeader("x-client-id", properties.getPayos().getClientId())
                .defaultHeader("x-api-key", properties.getPayos().getApiKey())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstTransaction(Object value) {
        if (value instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            Object first = map.values().iterator().next();
            if (first instanceof Map<?, ?> transaction) {
                return (Map<String, Object>) transaction;
            }
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
