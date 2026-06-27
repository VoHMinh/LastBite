package com.LastBite.modules.bag.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeeklyStockPlanItemResponse {
    private int dayOfWeek;
    private int quantity;
}
