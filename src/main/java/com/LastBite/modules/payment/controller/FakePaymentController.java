package com.LastBite.modules.payment.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.modules.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/fake")
@RequiredArgsConstructor
@Tag(name = "Fake Payment (Dev Only)", description = "Development/test endpoints for fake payment gateway")
public class FakePaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Simulate payment success", description = "Marks a pending payment as succeeded. For development/testing only.")
    @PostMapping("/simulate-success")
    public ResponseEntity<ApiResponse<String>> simulatePaymentSuccess(
            @Parameter(description = "The provider order code (e.g. 1234567890)") @RequestParam Long orderCode,
            @Parameter(description = "Reference ID for the simulated transaction") @RequestParam(defaultValue = "TEST") String reference) {
        paymentService.simulatePaymentSuccess(orderCode, reference);
        return ResponseEntity.ok(ApiResponse.ok("Payment simulated successfully", null));
    }
}
