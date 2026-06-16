package com.LastBite.modules.payment.gateway;

public record PaymentLinkResult(
        String paymentLinkId,
        String status,
        String checkoutUrl,
        String qrCode,
        String rawResponse
) {
}
