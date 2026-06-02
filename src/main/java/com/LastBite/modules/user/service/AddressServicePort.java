package com.LastBite.modules.user.service;

import com.LastBite.modules.user.dto.request.AddressRequest;
import com.LastBite.modules.user.dto.response.AddressResponse;

import java.util.List;
import java.util.UUID;

public interface AddressServicePort {

    List<AddressResponse> getAddresses(UUID userId);

    AddressResponse addAddress(UUID userId, AddressRequest request);

    AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request);

    void deleteAddress(UUID userId, UUID addressId);

    AddressResponse setDefault(UUID userId, UUID addressId);
}
