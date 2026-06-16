package com.LastBite.modules.store.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class StoreSpecialHoursRequest {

    @NotNull
    private LocalDate specialDate;

    private boolean closed;

    private LocalTime openTime;

    private LocalTime closeTime;

    @Size(max = 500)
    private String reason;
}
