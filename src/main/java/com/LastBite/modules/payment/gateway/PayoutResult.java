package com.LastBite.modules.payment.gateway;

public record PayoutResult(
        String providerPayoutId,
        String providerTransactionId,
        String state,
        String rawResponse
) {
}
