package com.LastBite.modules.payment.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.payments", name = "gateway", havingValue = "fake", matchIfMissing = true)
public class FakePaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public PaymentLinkResult createPaymentLink(CreatePaymentLinkCommand command) {
        String id = "fake_" + command.orderCode();
        return new PaymentLinkResult(
                id,
                "PENDING",
                "https://pay.local/checkout/" + id,
                "FAKE_QR_" + command.orderCode(),
                "{\"code\":\"00\",\"desc\":\"fake\",\"data\":{\"paymentLinkId\":\"" + id + "\"}}"
        );
    }

    @Override
    public void cancelPaymentLink(Long orderCode, String reason) {
        // Fake gateway intentionally has no side effect.
    }
}
