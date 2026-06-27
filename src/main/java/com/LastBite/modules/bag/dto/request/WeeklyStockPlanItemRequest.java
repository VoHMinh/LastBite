package com.LastBite.modules.bag.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WeeklyStockPlanItemRequest {

    @NotNull(message = "Ngay trong tuan khong duoc de trong")
    @Min(value = 0, message = "Ngay trong tuan phai tu 0 den 6")
    @Max(value = 6, message = "Ngay trong tuan phai tu 0 den 6")
    private Integer dayOfWeek;

    @NotNull(message = "So luong mac dinh khong duoc de trong")
    @Min(value = 0, message = "So luong mac dinh khong duoc am")
    @Max(value = 50, message = "Moi tui chi duoc set toi da 50 phan/ngay")
    private Integer quantity;
}
