package com.LastBite.modules.payment.gateway;

import com.LastBite.modules.payment.config.PaymentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payments", name = "gateway", havingValue = "payos")
public class PayOsPaymentGatewayAdapter implements PaymentGatewayPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final PaymentProperties properties;
    private final PayOsSignatureService signatureService;

    @Override
    @SuppressWarnings("unchecked")
    public PaymentLinkResult createPaymentLink(CreatePaymentLinkCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderCode", command.orderCode());
        payload.put("amount", command.amount().longValue());
        payload.put("description", command.description());
        payload.put("buyerName", command.buyerName());
        payload.put("buyerEmail", command.buyerEmail());
        payload.put("buyerPhone", command.buyerPhone());
        payload.put("items", new Object[]{
                Map.of("name", command.itemName(), "quantity", command.quantity(), "price", command.amount().longValue())
        });
        payload.put("cancelUrl", command.cancelUrl());
        payload.put("returnUrl", command.returnUrl());
        payload.put("expiredAt", command.expiresAt().getEpochSecond());
        payload.put("signature", signatureService.signCreatePaymentLink(
                command.orderCode(), command.amount(), command.description(), command.returnUrl(), command.cancelUrl()));

        Map<String, Object> response = restClient().post()
                .uri("/v2/payment-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);
        Map<String, Object> data = response == null ? Map.of() : (Map<String, Object>) response.getOrDefault("data", Map.of());
        return new PaymentLinkResult(
                stringValue(data.get("paymentLinkId")),
                stringValue(data.get("status")),
                stringValue(data.get("checkoutUrl")),
                stringValue(data.get("qrCode")),
                toJson(response)
        );
    }

    @Override
    public void cancelPaymentLink(Long orderCode, String reason) {
        restClient().post()
                .uri("/v2/payment-requests/{id}/cancel", orderCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("cancellationReason", reason == null ? "Cancelled by LastBite" : reason))
                .retrieve()
                .toBodilessEntity();
    }

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(properties.getPayos().getBaseUrl())
                .defaultHeader("x-client-id", properties.getPayos().getClientId())
                .defaultHeader("x-api-key", properties.getPayos().getApiKey())
                .build();
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
