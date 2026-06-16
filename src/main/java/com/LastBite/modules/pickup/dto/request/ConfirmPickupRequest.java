package com.LastBite.modules.pickup.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ConfirmPickupRequest {
    @NotNull
    private UUID orderId;
    private String pickupCode;
    private String qrToken;
    private String notes;
}
