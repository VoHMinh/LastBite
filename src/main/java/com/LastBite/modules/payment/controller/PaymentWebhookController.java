package com.LastBite.modules.payment.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.payment.dto.request.PayOsWebhookRequest;
import com.LastBite.modules.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/payos")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> webhook(@Valid @RequestBody PayOsWebhookRequest request) {
        paymentService.handlePayOsWebhook(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Webhook processed"));
    }
}
