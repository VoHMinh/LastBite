package com.LastBite.modules.pickup.job;

import com.LastBite.modules.pickup.service.PickupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PickupLifecycleJob {

    private final PickupService pickupService;

    @Scheduled(
            fixedDelayString = "${app.pickup.no-show-fixed-delay-ms:60000}",
            initialDelayString = "${app.pickup.no-show-initial-delay-ms:30000}"
    )
    public void expireNoShows() {
        int count = pickupService.expireNoShows();
        if (count > 0) {
            log.info("Marked {} order(s) as no-show", count);
        }
    }
}
