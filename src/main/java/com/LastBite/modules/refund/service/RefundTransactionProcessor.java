package com.LastBite.modules.refund.service;

import com.LastBite.common.security.SensitiveDataCipher;
import com.LastBite.modules.payment.entity.PaymentGatewayRequest;
import com.LastBite.modules.payment.enums.GatewayRequestStatus;
import com.LastBite.modules.payment.enums.GatewayRequestType;
import com.LastBite.modules.payment.enums.PaymentProvider;
import com.LastBite.modules.payment.gateway.CreatePayoutCommand;
import com.LastBite.modules.payment.gateway.PayoutGatewayPort;
import com.LastBite.modules.payment.gateway.PayoutResult;
import com.LastBite.modules.payment.repository.PaymentGatewayRequestRepository;
import com.LastBite.modules.refund.entity.RefundRequest;
import com.LastBite.modules.refund.entity.RefundTransaction;
import com.LastBite.modules.refund.enums.RefundStatus;
import com.LastBite.modules.refund.enums.RefundTransactionMethod;
import com.LastBite.modules.refund.enums.RefundTransactionStatus;
import com.LastBite.modules.refund.repository.RefundTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundTransactionProcessor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final RefundTransactionRepository transactionRepository;
    private final PaymentGatewayRequestRepository gatewayRequestRepository;
    private final PayoutGatewayPort payoutGateway;
    private final SensitiveDataCipher cipher;
    private final RefundService refundService;

    @Transactional
    public int processPending(int limit) {
        var transactions = transactionRepository.findPendingForProcessing(
                RefundTransactionStatus.PENDING,
                RefundTransactionMethod.PAYOS_PAYOUT,
                PageRequest.of(0, Math.max(1, limit)));
        int processed = 0;
        for (RefundTransaction transaction : transactions) {
            processOne(transaction);
            processed++;
        }
        return processed;
    }

    private void processOne(RefundTransaction transaction) {
        RefundRequest refund = transaction.getRefundRequest();
        PaymentGatewayRequest requestLog = gatewayRequestRepository
                .findByProviderAndIdempotencyKey(PaymentProvider.PAYOS, transaction.getIdempotencyKey())
                .orElseGet(() -> gatewayRequestRepository.save(PaymentGatewayRequest.builder()
                        .refundTransaction(transaction)
                        .provider(PaymentProvider.PAYOS)
                        .requestType(GatewayRequestType.CREATE_REFUND)
                        .idempotencyKey(transaction.getIdempotencyKey())
                        .status(GatewayRequestStatus.PENDING)
                        .build()));
        requestLog.setRefundTransaction(transaction);
        requestLog.setStatus(GatewayRequestStatus.PENDING);
        requestLog.setErrorMessage(null);
        requestLog.setRequestPayload(maskedRequestPayload(refund, transaction));

        try {
            refundService.markProcessing(transaction.getId());
            String accountNumber = cipher.decrypt(refund.getRefundAccountNumberEncrypted());
            PayoutResult result = payoutGateway.createPayout(new CreatePayoutCommand(
                    "refund_" + refund.getId(),
                    transaction.getAmount(),
                    "LastBite refund " + refund.getId().toString().substring(0, 8),
                    refund.getRefundBankCode(),
                    accountNumber,
                    transaction.getIdempotencyKey(),
                    "customer_refund"));
            String providerReference = result.providerTransactionId() == null
                    ? result.providerPayoutId()
                    : result.providerTransactionId();
            requestLog.setStatus(GatewayRequestStatus.SUCCEEDED);
            requestLog.setProviderReference(providerReference);
            requestLog.setResponsePayload(result.rawResponse());

            if (isPaidState(result.state())) {
                refundService.completeGatewayTransaction(transaction.getId(), providerReference, result.rawResponse());
            } else {
                transaction.setProviderReference(providerReference);
                transaction.setRawPayload(result.rawResponse());
                refund.setStatus(RefundStatus.PROCESSING);
            }
        } catch (Exception ex) {
            log.warn("Refund payout failed for transaction {}", transaction.getId(), ex);
            requestLog.setStatus(GatewayRequestStatus.FAILED);
            requestLog.setErrorMessage(ex.getMessage());
            refundService.failGatewayTransaction(transaction.getId(), ex.getMessage(), null);
        }
    }

    private boolean isPaidState(String state) {
        return "SUCCEEDED".equalsIgnoreCase(state)
                || "SUCCESS".equalsIgnoreCase(state)
                || "PAID".equalsIgnoreCase(state);
    }

    private String maskedRequestPayload(RefundRequest refund, RefundTransaction transaction) {
        return toJson(Map.of(
                "referenceId", "refund_" + refund.getId(),
                "amount", transaction.getAmount(),
                "toBin", refund.getRefundBankCode(),
                "toAccountNumber", "****" + refund.getRefundAccountNumberLast4(),
                "category", "customer_refund"
        ));
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
