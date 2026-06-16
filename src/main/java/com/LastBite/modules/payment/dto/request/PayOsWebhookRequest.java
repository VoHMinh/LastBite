package com.LastBite.modules.payment.dto.request;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class PayOsWebhookRequest {
    private String code;
    private String desc;
    private boolean success;
    private Map<String, Object> data = new HashMap<>();
    private String signature;
}
