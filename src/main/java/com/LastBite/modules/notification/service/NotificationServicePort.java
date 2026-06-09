package com.LastBite.modules.notification.service;

import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.notification.dto.request.BroadcastNotificationRequest;
import com.LastBite.modules.notification.dto.response.BroadcastNotificationResponse;
import com.LastBite.modules.order.entity.Order;

public interface NotificationServicePort {

    void notifyOrderReserved(Order order);

    void notifyPaymentExpiring(Order order);

    void notifyPickupReminder(Order order, int minutesBefore);

    void notifyPickupWindowOpen(Order order);

    void notifyPickupEndingSoon(Order order);

    void notifyFavoriteStoreStockAvailable(BagDailyStock stock);

    BroadcastNotificationResponse broadcastToCustomers(BroadcastNotificationRequest request);
}
