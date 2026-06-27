package com.LastBite.modules.notification.job;

import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.service.SurpriseBagServicePort;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationReminderJob {

    private static final List<OrderStatus> PICKUP_REMINDER_STATUSES = List.of(
            OrderStatus.PAID,
            OrderStatus.READY_FOR_PICKUP
    );

    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final BagDailyStockRepository stockRepository;
    private final SurpriseBagServicePort surpriseBagService;
    private final NotificationServicePort notificationService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.notifications.reminder-fixed-delay-ms:60000}")
    public void sendPaymentAndPickupReminders() {
        Instant now = Instant.now(clock);
        sendPaymentExpiringReminders(now);
        sendPickupReminders();
    }

    private void sendPaymentExpiringReminders(Instant now) {
        Instant threshold = now.plus(Duration.ofMinutes(3));
        var orders = orderRepository.findPendingPaymentsExpiring(now, threshold);
        for (var order : orders) {
            notificationService.notifyPaymentExpiring(order);
        }
        if (!orders.isEmpty()) {
            log.info("Queued {} payment expiry reminder notification(s)", orders.size());
        }
    }

    private void sendPickupReminders() {
        LocalDate today = LocalDate.now(clock);
        LocalTime nowTime = LocalTime.now(clock);
        sendPickupStartingSoon(today, nowTime, 60);
        sendPickupStartingSoon(today, nowTime, 30);
        sendPickupStartingSoon(today, nowTime, 10);

        var openOrders = orderRepository.findPickupWindowOpen(PICKUP_REMINDER_STATUSES, today, nowTime);
        for (var order : openOrders) {
            notificationService.notifyPickupWindowOpen(order);
        }

        var endingOrders = orderRepository.findPickupEndingSoon(
                PICKUP_REMINDER_STATUSES,
                today,
                nowTime,
                nowTime.plusMinutes(15));
        for (var order : endingOrders) {
            notificationService.notifyPickupEndingSoon(order);
        }
    }

    private void sendPickupStartingSoon(LocalDate today, LocalTime nowTime, int minutesBefore) {
        LocalTime threshold = nowTime.plusMinutes(minutesBefore);
        var orders = orderRepository.findPickupStartingSoon(PICKUP_REMINDER_STATUSES, today, nowTime, threshold);
        for (var order : orders) {
            notificationService.notifyPickupReminder(order, minutesBefore);
        }
    }
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sendMerchantStockSetupReminders() {
        int generated = surpriseBagService.createUpcomingStocks();
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        var stores = storeRepository.findAllByStatusAndVerificationStatus(StoreStatus.ACTIVE, VerificationStatus.VERIFIED);
        for (var store : stores) {
            var stocks = stockRepository.findByStoreIdAndDateWithBag(store.getId(), tomorrow);
            int totalQuantity = stocks.stream().mapToInt(stock -> Math.max(0, stock.getQuantity())).sum();
            int bagCount = (int) stocks.stream().filter(stock -> stock.getQuantity() > 0).count();
            notificationService.notifyMerchantTomorrowStockSummary(store, tomorrow, totalQuantity, bagCount);
        }
        if (!stores.isEmpty()) {
            log.info("Queued {} merchant tomorrow stock summary notification batch(es); generated {} forecast stock(s)",
                    stores.size(), generated);
        }
    }
}
