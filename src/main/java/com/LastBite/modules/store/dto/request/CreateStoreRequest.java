package com.LastBite.modules.store.dto.request;

import com.LastBite.modules.store.enums.StoreCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStoreRequest {

    @NotBlank(message = "Tên cửa hàng không được để trống")
    @Size(max = 255, message = "Tên cửa hàng tối đa 255 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Danh mục không được để trống")
    private StoreCategory category;

    @Size(max = 20)
    private String phone;

    @Size(max = 255)
    private String email;

    @NotBlank(message = "Địa chỉ không được để trống")
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
