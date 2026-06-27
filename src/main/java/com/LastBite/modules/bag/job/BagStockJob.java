package com.LastBite.modules.bag.job;

import com.LastBite.modules.bag.service.SurpriseBagServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BagStockJob {

    private final SurpriseBagServicePort surpriseBagService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void createTodayStocks() {
        int created = surpriseBagService.createTodayStocks();
        log.info("Tồn kho ngày mới: đã tạo {} bản ghi bag_daily_stocks", created);
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void createUpcomingStocks() {
        int created = surpriseBagService.createUpcomingStocks();
        log.info("Forecast ton kho: da tao {} ban ghi bag_daily_stocks cho cac ngay toi", created);
    }

    @Scheduled(cron = "0 55 23 * * *", zone = "Asia/Ho_Chi_Minh")
    public void expireUnsoldStocks() {
        int expired = surpriseBagService.expireUnsoldStocks();
        log.info("Hết hạn tồn kho cuối ngày: đã đánh dấu {} bản ghi chưa bán", expired);
    }
}
