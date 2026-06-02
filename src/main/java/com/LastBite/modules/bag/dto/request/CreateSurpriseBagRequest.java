package com.LastBite.modules.bag.dto.request;

import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.store.enums.StoreCategory;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class CreateSurpriseBagRequest {

    @NotBlank(message = "Tên túi không được để trống")
    @Size(max = 255, message = "Tên túi tối đa 255 ký tự")
    private String name;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    private BagType bagType = BagType.STANDARD;

    private DietType dietType = DietType.MEAT;

    @NotNull(message = "Danh mục túi không được để trống")
    private StoreCategory category;

    @NotNull(message = "Kích cỡ túi không được để trống")
    private BagSize bagSize;

    private List<@NotBlank(message = "URL ảnh không được để trống") String> photos;

    private Boolean dynamicPricingEnabled = true;

    @Min(value = 1, message = "Mỗi đơn phải cho mua ít nhất 1 túi")
    @Max(value = 3, message = "Mỗi khách tối đa 3 túi/ngày/cửa hàng")
    private Integer maxPerOrder = 1;

    private Boolean containerProvided = true;

    private Boolean carrierBagProvided = true;

    @Size(max = 500, message = "Ghi chu bao bi toi da 500 ky tu")
    private String packagingNote;

    @NotNull(message = "Giờ bắt đầu pickup không được để trống")
    private LocalTime pickupStartTime;

    @NotNull(message = "Giờ kết thúc pickup không được để trống")
    private LocalTime pickupEndTime;

    @NotEmpty(message = "Cần chọn ít nhất 1 ngày bán")
    private Set<@Min(value = 0, message = "Ngày bán phải từ 0 đến 6")
                @Max(value = 6, message = "Ngày bán phải từ 0 đến 6") Integer> availableDays;
}
