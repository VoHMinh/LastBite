package com.LastBite.modules.store.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import com.LastBite.modules.store.enums.StoreCategory;

@Data
public class UpdateStoreRequest {

    @Size(max = 255, message = "Tên cửa hàng tối đa 255 ký tự")
    private String name;

    private String description;

    private StoreCategory category;

    @Size(max = 20)
    private String phone;

    @Size(max = 255)
    private String email;

    private String address;

    @Size(max = 100, message = "Quận/Huyện tối đa 100 ký tự")
    private String district;

    @Size(max = 100, message = "Thành phố tối đa 100 ký tự")
    private String city;

    private Double lat;
    private Double lng;

    private String pickupInstructions;

    @Size(max = 500)
    private String coverImageUrl;

    @Size(max = 500)
    private String logoUrl;

    @Size(max = 100)
    private String businessLicenseNumber;

    @Size(max = 500)
    private String businessLicenseImageUrl;
}
