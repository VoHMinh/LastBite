package com.LastBite.modules.notification.service;

import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.notification.dto.request.BroadcastNotificationRequest;
import com.LastBite.modules.notification.dto.response.BroadcastNotificationResponse;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.promotion.entity.UserVoucher;
import com.LastBite.modules.promotion.entity.VoucherCampaign;
import com.LastBite.modules.store.entity.Store;

import java.time.Instant;

public interface NotificationServicePort {

    void notifyOrderReserved(Order order);

    void notifyOrderReadyForPickup(Order order);

    void notifyPaymentSuccess(Order order);

    void notifyPaymentFailed(Order order);

    void notifyOrderExpired(Order order);

    void notifyOrderCancelled(Order order);

    void notifyOrderRefunded(Order order);

    void notifyOrderPickedUp(Order order);

    void notifyOrderDelegatePickup(Order order, User delegate);

    void notifyImpactMilestone(User user, int savedMeals);

    void notifyOrderMissedPickup(Order order, Instant disputeWindowUntil);

    void notifyPaymentExpiring(Order order);

    void notifyPickupReminder(Order order, int minutesBefore);

    void notifyPickupWindowOpen(Order order);

    void notifyPickupEndingSoon(Order order);

    void notifyFavoriteStoreStockAvailable(BagDailyStock stock);

    void notifyVoucherAvailable(UserVoucher userVoucher);

    void notifyMerchantNewPaidOrder(Order order);

    void notifyMerchantOrderPickedUp(Order order);

    void notifyMerchantOrderExpired(Order order);

    void notifyMerchantPickupSoon(Order order);

    void notifyMerchantStockLow(BagDailyStock stock);

    void notifyMerchantSetStockReminder(Store store);

    void notifyMerchantStoreApproved(Store store);

    void notifyMerchantStoreRejected(Store store, String reason);

    void notifyMerchantBagPaused(SurpriseBag bag);

    void notifyMerchantBagResumed(SurpriseBag bag);

    void notifyMerchantCampaignStarted(VoucherCampaign campaign, Store store);

    void notifyAdminStorePendingReview(Store store);

    void notifyAdminRefundRequired(Order order, String failureReason);

    BroadcastNotificationResponse broadcastToCustomers(BroadcastNotificationRequest request);
}