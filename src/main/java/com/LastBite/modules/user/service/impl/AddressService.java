package com.LastBite.modules.user.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.user.dto.request.AddressRequest;
import com.LastBite.modules.user.dto.response.AddressResponse;
import com.LastBite.modules.user.entity.UserAddress;
import com.LastBite.modules.user.repository.UserAddressRepository;
import com.LastBite.modules.user.service.AddressServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService implements AddressServicePort {

    private static final int MAX_ADDRESSES = 10;

    private final UserAddressRepository addressRepository;
    private final UserRepository userRepository;

    /**
     * Lấy toàn bộ địa chỉ của người dùng (có cache).
     */
    @Cacheable(value = "user-addresses", key = "#userId")
    public List<AddressResponse> getAddresses(UUID userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Thêm địa chỉ mới — xóa cache liên quan.
     */
    @Transactional
    @CacheEvict(value = "user-addresses", key = "#userId")
    public AddressResponse addAddress(UUID userId, AddressRequest request) {
        if (addressRepository.countByUserId(userId) >= MAX_ADDRESSES) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Tối đa " + MAX_ADDRESSES + " địa chỉ");
        }

        User user = userRepository.getReferenceById(userId);

        boolean setDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (setDefault) {
            addressRepository.clearDefaultByUserId(userId);
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .label(request.getLabel() != null ? request.getLabel().toUpperCase() : "HOME")
                .fullAddress(request.getFullAddress().trim())
                .lat(request.getLat())
                .lng(request.getLng())
                .isDefault(setDefault)
                .build();

        address = addressRepository.save(address);
        log.info("Đã thêm địa chỉ cho người dùng {}: {}", userId, address.getId());
        return toResponse(address);
    }

    /**
     * Cập nhật địa chỉ hiện có — xóa cache liên quan.
     */
    @Transactional
    @CacheEvict(value = "user-addresses", key = "#userId")
    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request) {
        UserAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa chỉ"));

        if (request.getLabel() != null) address.setLabel(request.getLabel().toUpperCase());
        if (request.getFullAddress() != null) address.setFullAddress(request.getFullAddress().trim());
        if (request.getLat() != null) address.setLat(request.getLat());
        if (request.getLng() != null) address.setLng(request.getLng());

        if (Boolean.TRUE.equals(request.getIsDefault()) && !address.isDefault()) {
            addressRepository.clearDefaultByUserId(userId);
            address.setDefault(true);
        }

        address = addressRepository.save(address);
        return toResponse(address);
    }

    /**
     * Xóa địa chỉ — xóa cache liên quan.
     */
    @Transactional
    @CacheEvict(value = "user-addresses", key = "#userId")
    public void deleteAddress(UUID userId, UUID addressId) {
        UserAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa chỉ"));
        addressRepository.delete(address);
        log.info("Đã xóa địa chỉ {} của người dùng {}", addressId, userId);
    }

    /**
     * Đặt địa chỉ làm mặc định — xóa cache liên quan.
     */
    @Transactional
    @CacheEvict(value = "user-addresses", key = "#userId")
    public AddressResponse setDefault(UUID userId, UUID addressId) {
        UserAddress address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy địa chỉ"));

        addressRepository.clearDefaultByUserId(userId);
        address.setDefault(true);
        address = addressRepository.save(address);
        return toResponse(address);
    }

    private AddressResponse toResponse(UserAddress a) {
        return AddressResponse.builder()
                .id(a.getId())
                .label(a.getLabel())
                .fullAddress(a.getFullAddress())
                .lat(a.getLat())
                .lng(a.getLng())
                .isDefault(a.isDefault())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
