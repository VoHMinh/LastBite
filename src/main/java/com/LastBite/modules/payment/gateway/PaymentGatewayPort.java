package com.LastBite.modules.payment.gateway;

public interface PaymentGatewayPort {
    PaymentLinkResult createPaymentLink(CreatePaymentLinkCommand command);
    void cancelPaymentLink(Long orderCode, String reason);
}
