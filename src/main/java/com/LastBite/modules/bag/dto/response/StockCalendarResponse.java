package com.LastBite.modules.bag.dto.response;

import com.LastBite.modules.bag.enums.DailyStockSource;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class StockCalendarResponse {
    private UUID dailyStockId;
    private LocalDate date;
    private int quantity;
    private int reserved;
    private int sold;
    private int available;
    private DailyStockStatus status;
    private DailyStockSource source;
}
