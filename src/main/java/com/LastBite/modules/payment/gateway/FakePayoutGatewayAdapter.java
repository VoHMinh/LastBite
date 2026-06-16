package com.LastBite.modules.payment.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.payments", name = "gateway", havingValue = "fake", matchIfMissing = true)
public class FakePayoutGatewayAdapter implements PayoutGatewayPort {

    @Override
    public PayoutResult createPayout(CreatePayoutCommand command) {
        String id = "fake_payout_" + command.referenceId();
        return new PayoutResult(
                id,
                "fake_txn_" + command.referenceId(),
                "PROCESSING",
                "{\"code\":\"00\",\"desc\":\"fake\",\"data\":{\"id\":\"" + id + "\"}}"
        );
    }
}
