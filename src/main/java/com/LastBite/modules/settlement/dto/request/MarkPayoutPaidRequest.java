package com.LastBite.modules.settlement.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarkPayoutPaidRequest {

    @Size(max = 160)
    private String providerPayoutId;

    @Size(max = 160)
    private String providerTransactionId;
}
