package com.LastBite.modules.promotion.dto.request;

import com.LastBite.modules.promotion.enums.VoucherCodeType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class AddVoucherCodesRequest {

    @NotEmpty(message = "Can truyen it nhat 1 ma voucher")
    @Size(max = 500, message = "Moi lan chi import toi da 500 ma")
    private List<@Size(max = 80, message = "Ma voucher toi da 80 ky tu") String> codes;

    private VoucherCodeType codeType = VoucherCodeType.PUBLIC;
    private Integer usageLimit;
    private Instant startsAt;
    private Instant endsAt;
}
