package com.LastBite.common.controller;

import com.LastBite.common.response.ApiResponse;
import com.LastBite.common.service.ResendWebhookService;
import com.LastBite.common.service.ResendWebhookVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mail/resend")
@RequiredArgsConstructor
public class ResendWebhookController {

    private final ResendWebhookVerifier verifier;
    private final ResendWebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> receive(
            @RequestBody String payload,
            @RequestHeader HttpHeaders headers) {
        if (!verifier.verify(payload, headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<Void>builder()
                            .code(4010)
                            .message("Invalid webhook signature")
                            .build());
        }
        webhookService.handle(payload);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
