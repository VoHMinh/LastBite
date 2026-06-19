package com.LastBite.modules.promotion.dto.request;

import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.promotion.enums.VoucherCampaignOwnerType;
import com.LastBite.modules.promotion.enums.VoucherDiscountType;
import com.LastBite.modules.promotion.enums.VoucherFundingSource;
import com.LastBite.modules.store.enums.StoreCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class VoucherCampaignUpsertRequest {

    private VoucherCampaignOwnerType ownerType;

    private UUID storeId;
    private UUID bagId;
    private StoreCategory category;
    private BagType bagType;
    private DietType dietType;

    @NotBlank(message = "Ten campaign khong duoc de trong")
    @Size(max = 150, message = "Ten campaign toi da 150 ky tu")
    private String name;

    @Size(max = 1000, message = "Mo ta campaign toi da 1000 ky tu")
    private String description;

    @NotNull(message = "Loai giam gia khong duoc de trong")
    private VoucherDiscountType discountType;

    @NotNull(message = "Gia tri giam gia khong duoc de trong")
    @DecimalMin(value = "0.01", message = "Gia tri giam gia phai lon hon 0")
    private BigDecimal discountValue;

    @DecimalMin(value = "0", message = "Muc giam toi da khong hop le")
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0", message = "Gia tri don toi thieu khong hop le")
    private BigDecimal minOrderAmount;

    @NotNull(message = "Nguon tai tro voucher khong duoc de trong")
    private VoucherFundingSource fundingSource;

    @Min(value = 0, message = "Platform funding bps khong hop le")
    private Integer platformFundingBps;

    @Min(value = 0, message = "Merchant funding bps khong hop le")
    private Integer merchantFundingBps;

    @NotNull(message = "Thoi diem bat dau khong duoc de trong")
    private Instant startsAt;

    @NotNull(message = "Thoi diem ket thuc khong duoc de trong")
    private Instant endsAt;

    @DecimalMin(value = "0", message = "Ngan sach khong hop le")
    private BigDecimal budgetLimitAmount;

    @Min(value = 1, message = "Gioi han tong luot dung phai lon hon 0")
    private Integer totalUsageLimit;

    @Min(value = 1, message = "Gioi han moi user phai lon hon 0")
    private Integer perUserLimit;

    private boolean firstOrderOnly;
}
