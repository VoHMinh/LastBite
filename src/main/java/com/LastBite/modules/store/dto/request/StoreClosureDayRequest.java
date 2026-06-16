package com.LastBite.modules.store.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StoreClosureDayRequest {

    @NotNull
    private LocalDate closedDate;

    @Size(max = 500)
    private String reason;
}
