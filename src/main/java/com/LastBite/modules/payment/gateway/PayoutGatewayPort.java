package com.LastBite.modules.payment.gateway;

public interface PayoutGatewayPort {
    PayoutResult createPayout(CreatePayoutCommand command);
}
