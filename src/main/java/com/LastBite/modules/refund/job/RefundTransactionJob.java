package com.LastBite.modules.refund.job;

import com.LastBite.modules.refund.service.RefundTransactionProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundTransactionJob {

    private final RefundTransactionProcessor processor;

    @Scheduled(fixedDelayString = "${app.refunds.process-fixed-delay-ms:60000}")
    public void processPendingRefunds() {
        int processed = processor.processPending(20);
        if (processed > 0) {
            log.info("Processed {} pending refund payout transaction(s)", processed);
        }
    }
}
