package com.LastBite.modules.payment.job;

import com.LastBite.modules.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentLifecycleJob {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${app.payments.expire-fixed-delay-ms:60000}")
    public void expirePendingPayments() {
        int expired = paymentService.expirePendingPayments();
        if (expired > 0) {
            log.info("Expired {} pending payment order(s)", expired);
        }
    }
}
