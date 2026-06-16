package com.LastBite.modules.settlement.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class MerchantPayableBalanceResponse {
    private UUID businessProfileId;
    private BigDecimal balance;
    private String currency;
}
