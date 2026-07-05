package com.LastBite.modules.user.dto.request;

import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.user.enums.CollectionTimeSlot;
import com.LastBite.modules.user.enums.PreferredDiet;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateDiscoveryPreferenceRequest {

    private PreferredDiet preferredDiet;

    private BagType preferredBagType;

    private Set<CollectionTimeSlot> preferredCollectionTimes;

    @Size(max = 255, message = "Location label must be at most 255 characters")
    private String defaultLocationLabel;

    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    private Double defaultLat;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    private Double defaultLng;

    @DecimalMin(value = "0.0", inclusive = false, message = "Radius must be greater than 0 km")
    @DecimalMax(value = "50.0", message = "Radius must be at most 50 km")
    private Double defaultRadiusKm;
}
