package com.LastBite.modules.payment.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.util.HashUtil;
import com.LastBite.modules.audit.enums.AuditActorType;
import com.LastBite.modules.audit.service.OrderStatusHistoryService;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.StockAuditLog;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.bag.enums.StockAuditAction;
import com.LastBite.modules.bag.enums.StockAuditActorType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.StockAuditLogRepository;
import com.LastBite.modules.ledger.service.LedgerService;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.payment.config.PaymentProperties;
import com.LastBite.modules.payment.dto.request.PayOsWebhookRequest;
import com.LastBite.modules.payment.entity.Payment;
import com.LastBite.modules.payment.entity.PaymentGatewayRequest;
import com.LastBite.modules.payment.entity.PaymentTransaction;
import com.LastBite.modules.payment.entity.PaymentWebhook;
import com.LastBite.modules.payment.enums.*;
import com.LastBite.modules.payment.gateway.CreatePaymentLinkCommand;
import com.LastBite.modules.payment.gateway.PayOsSignatureService;
import com.LastBite.modules.payment.gateway.PaymentGatewayPort;
import com.LastBite.modules.payment.gateway.PaymentLinkResult;
import com.LastBite.modules.payment.repository.PaymentGatewayRequestRepository;
import com.LastBite.modules.payment.repository.PaymentRepository;
import com.LastBite.modules.payment.repository.PaymentTransactionRepository;
import com.LastBite.modules.payment.repository.PaymentWebhookRepository;
import com.LastBite.modules.promotion.service.VoucherApplicationService;
import com.LastBite.modules.refund.enums.RefundReason;
import com.LastBite.modules.refund.service.RefundService;
import com.LastBite.modules.store.service.StoreReliabilityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentWebhookRepository webhookRepository;
    private final PaymentGatewayRequestRepository gatewayRequestRepository;
    private final OrderRepository orderRepository;
    private final BagDailyStockRepository stockRepository;
    private final StockAuditLogRepository stockAuditLogRepository;
    private final PaymentGatewayPort paymentGateway;
    private final PayOsSignatureService signatureService;
    private final PaymentProperties properties;
    private final OrderStatusHistoryService statusHistoryService;
    private final LedgerService ledgerService;
    private final RefundService refundService;
    private final VoucherApplicationService voucherApplicationService;
    private final StoreReliabilityService reliabilityService;
    private final Clock clock;

    @Transactional
    public Payment createPaymentForOrder(Order order) {
        Optional<Payment> existing = paymentRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant expiresAt = order.getPaymentExpiresAt() == null ? order.getReservedUntil() : order.getPaymentExpiresAt();
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .user(order.getUser())
                .provider(provider())
                .providerOrderCode(generateProviderOrderCode())
                .amount(order.getFinalAmount())
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .expiresAt(expiresAt)
                .idempotencyKey("pay:" + order.getId())
                .build());

        String requestPayload = toJson(Map.of(
                "orderCode", payment.getProviderOrderCode(),
                "amount", payment.getAmount(),
                "orderId", order.getId().toString()
        ));
        PaymentGatewayRequest gatewayRequest = gatewayRequestRepository.save(PaymentGatewayRequest.builder()
                .payment(payment)
                .provider(payment.getProvider())
                .requestType(GatewayRequestType.CREATE_PAYMENT_LINK)
                .idempotencyKey(payment.getIdempotencyKey())
                .status(GatewayRequestStatus.PENDING)
                .requestPayload(requestPayload)
                .build());

        try {
            PaymentLinkResult result = paymentGateway.createPaymentLink(new CreatePaymentLinkCommand(
                    payment.getProviderOrderCode(),
                    payment.getAmount(),
                    shortPayOsDescription(order),
                    order.getUser().getFullName(),
                    order.getUser().getEmail(),
                    order.getUser().getPhone(),
                    order.getBag().getName(),
                    order.getQuantity(),
                    properties.getReturnUrl(),
                    properties.getCancelUrl(),
                    expiresAt
            ));
            payment.setProviderPaymentLinkId(result.paymentLinkId());
            payment.setCheckoutUrl(result.checkoutUrl());
            payment.setQrCode(result.qrCode());
            payment.setRawProviderPayload(result.rawResponse());
            gatewayRequest.setStatus(GatewayRequestStatus.SUCCEEDED);
            gatewayRequest.setProviderReference(result.paymentLinkId());
            gatewayRequest.setResponsePayload(result.rawResponse());
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            gatewayRequest.setStatus(GatewayRequestStatus.FAILED);
            gatewayRequest.setErrorMessage(e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Khong the tao link thanh toan PayOS");
        }
        return payment;
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public void handlePayOsWebhook(PayOsWebhookRequest request) {
        boolean valid = signatureService.verifyWebhook(request.getData(), request.getSignature());
        Long providerOrderCode = longValue(request.getData().get("orderCode"));
        BigDecimal amount = bigDecimalValue(request.getData().get("amount"));
        String reference = stringValue(request.getData().get("reference"));
        String payload = toJson(request);
        String eventKey = "PAYOS:" + providerOrderCode + ":" + reference + ":" + amount + ":"
                + HashUtil.sha256(payload).substring(0, 16);

        PaymentWebhook webhook = webhookRepository.findByEventKey(eventKey)
                .orElseGet(() -> webhookRepository.save(PaymentWebhook.builder()
                        .provider(PaymentProvider.PAYOS)
                        .eventKey(eventKey)
                        .providerOrderCode(providerOrderCode)
                        .signature(request.getSignature())
                        .payload(payload)
                        .validSignature(valid)
                        .processed(false)
                        .build()));
        if (webhook.isProcessed()) {
            return;
        }
        if (!valid || providerOrderCode == null || amount == null) {
            markWebhookIgnored(webhook, !valid ? "Invalid PayOS signature" : "Malformed PayOS webhook data");
            return;
        }

        Optional<Payment> paymentResult = paymentRepository.findByProviderOrderCode(providerOrderCode);
        if (paymentResult.isEmpty()) {
            markWebhookIgnored(webhook, "Unknown PayOS orderCode");
            return;
        }
        Payment payment = paymentResult.get();
        webhook.setPayment(payment);

        if (payment.getAmount().compareTo(amount) != 0) {
            markWebhookIgnored(webhook, "Amount mismatch");
            return;
        }

        if (request.isSuccess() && "00".equals(request.getCode())) {
            applySuccessfulPayment(payment, reference, request);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(request.getDesc());
        }
        webhook.setProcessed(true);
        webhook.setProcessedAt(Instant.now(clock));
    }

    private void markWebhookIgnored(PaymentWebhook webhook, String reason) {
        webhook.setFailureReason(reason);
        webhook.setProcessed(true);
        webhook.setProcessedAt(Instant.now(clock));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public int expirePendingPayments() {
        Instant now = Instant.now(clock);
        int expired = 0;
        for (Payment payment : paymentRepository.findByStatusAndExpiresAtLessThanEqual(PaymentStatus.PENDING, now)) {
            Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId()).orElse(null);
            if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                continue;
            }
            releaseReservedStock(order, "Het han thanh toan", StockAuditActorType.SYSTEM);
            voucherApplicationService.releaseForOrder(order, "Payment expired before capture");
            OrderStatus previous = order.getStatus();
            order.setStatus(OrderStatus.EXPIRED);
            order.setExpiredAt(now);
            payment.setStatus(PaymentStatus.EXPIRED);
            try {
                paymentGateway.cancelPaymentLink(payment.getProviderOrderCode(), "Payment expired");
            } catch (Exception ex) {
                log.warn("Cannot cancel PayOS payment link {}", payment.getProviderOrderCode(), ex);
            }
            statusHistoryService.record(order, previous, OrderStatus.EXPIRED, null, AuditActorType.SYSTEM,
                    "Payment reservation expired", null);
            expired++;
        }
        return expired;
    }

    public Optional<Payment> findByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    private void applySuccessfulPayment(Payment payment, String reference, PayOsWebhookRequest request) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }
        Instant now = Instant.now(clock);
        PaymentTransaction transaction = transactionRepository.findByProviderAndProviderTransactionId(payment.getProvider(), reference)
                .orElseGet(() -> transactionRepository.save(PaymentTransaction.builder()
                        .payment(payment)
                        .provider(payment.getProvider())
                        .providerTransactionId(reference)
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .status(PaymentTransactionStatus.SUCCEEDED)
                        .providerCode(stringValue(request.getData().get("code")))
                        .providerDescription(stringValue(request.getData().get("desc")))
                        .paidAt(now)
                        .rawPayload(toJson(request))
                        .build()));
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(transaction.getPaidAt());

        Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && !payment.getExpiresAt().isBefore(now)) {
            captureReservedStock(order);
            OrderStatus previous = order.getStatus();
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(now);
            order.setPaymentExpiresAt(payment.getExpiresAt());
            statusHistoryService.record(order, previous, OrderStatus.PAID, null, AuditActorType.PAYMENT_PROVIDER,
                    "PayOS payment succeeded", "reference=" + reference);
            voucherApplicationService.redeemForOrder(order);
            ledgerService.recordPaymentCaptured(order, payment);
            reliabilityService.recordOrderPaid(order);
        } else if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            releaseReservedStock(order, "Thanh toan ve tre sau khi het han", StockAuditActorType.PAYMENT_PROVIDER);
            voucherApplicationService.releaseForOrder(order, "Late payment after reservation expiry");
            OrderStatus previous = order.getStatus();
            order.setStatus(OrderStatus.EXPIRED);
            order.setExpiredAt(now);
            statusHistoryService.record(order, previous, OrderStatus.EXPIRED, null, AuditActorType.SYSTEM,
                    "Late PayOS webhook after reservation expiry", null);
            refundService.createAutoRefund(order, payment, RefundReason.PAYMENT_AFTER_EXPIRY,
                    "Thanh toan PayOS thanh cong sau khi reservation da het han");
        } else if (order.getStatus() == OrderStatus.EXPIRED || order.getStatus() == OrderStatus.CANCELLED) {
            refundService.createAutoRefund(order, payment, RefundReason.PAYMENT_AFTER_EXPIRY,
                    "Thanh toan PayOS thanh cong sau khi don da ket thuc");
        }
    }

    private void captureReservedStock(Order order) {
        BagDailyStock stock = stockRepository.findByBagIdAndDateForUpdate(order.getBag().getId(), order.getPickupDate())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        int availableBefore = stock.available();
        stock.setReserved(Math.max(0, stock.getReserved() - order.getQuantity()));
        stock.setSold(stock.getSold() + order.getQuantity());
        stock.setStatus(stock.available() <= 0 ? DailyStockStatus.SOLD_OUT : DailyStockStatus.ACTIVE);
        stockAuditLogRepository.save(StockAuditLog.builder()
                .bag(order.getBag())
                .dailyStock(stock)
                .actor(order.getUser())
                .actorType(StockAuditActorType.PAYMENT_PROVIDER)
                .action(StockAuditAction.SELL)
                .delta(-order.getQuantity())
                .quantityBefore(availableBefore)
                .quantityAfter(stock.available())
                .reason("PayOS payment captured")
                .orderId(order.getId())
                .build());
    }

    private void releaseReservedStock(Order order, String reason, StockAuditActorType actorType) {
        BagDailyStock stock = stockRepository.findByBagIdAndDateForUpdate(order.getBag().getId(), order.getPickupDate())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        int availableBefore = stock.available();
        stock.setReserved(Math.max(0, stock.getReserved() - order.getQuantity()));
        if (stock.getStatus() == DailyStockStatus.SOLD_OUT && stock.available() > 0) {
            stock.setStatus(DailyStockStatus.ACTIVE);
        }
        stockAuditLogRepository.save(StockAuditLog.builder()
                .bag(order.getBag())
                .dailyStock(stock)
                .actor(order.getUser())
                .actorType(actorType)
                .action(StockAuditAction.RESERVE_CANCEL)
                .delta(order.getQuantity())
                .quantityBefore(availableBefore)
                .quantityAfter(stock.available())
                .reason(reason)
                .orderId(order.getId())
                .build());
    }

    private PaymentProvider provider() {
        return "payos".equalsIgnoreCase(properties.getGateway()) ? PaymentProvider.PAYOS : PaymentProvider.FAKE;
    }

    private Long generateProviderOrderCode() {
        long millis = Instant.now(clock).toEpochMilli() % 9_000_000_000L;
        return 1_000_000_000L + millis + ThreadLocalRandom.current().nextInt(1000);
    }

    private String shortPayOsDescription(Order order) {
        return ("LB" + order.getOrderNumber().replace("LB-", "")).replaceAll("[^A-Za-z0-9]", "");
    }

    private Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal bigDecimalValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return BigDecimal.valueOf(number.longValue());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
