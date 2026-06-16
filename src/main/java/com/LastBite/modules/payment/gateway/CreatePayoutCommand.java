package com.LastBite.modules.payment.gateway;

import java.math.BigDecimal;

public record CreatePayoutCommand(
        String referenceId,
        BigDecimal amount,
        String description,
        String toBin,
        String toAccountNumber,
        String idempotencyKey
) {
}
