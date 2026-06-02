package com.LastBite.modules.bag.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.modules.bag.dto.admin.BagPriceTierRequest;
import com.LastBite.modules.bag.entity.BagPriceTier;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.repository.BagPriceTierRepository;
import com.LastBite.modules.bag.service.impl.AdminBagPriceTierService;
import com.LastBite.modules.store.enums.StoreCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBagPriceTierServiceTest {

    private final BagPriceTierRepository tierRepository = mock(BagPriceTierRepository.class);
    private AdminBagPriceTierService service;

    @BeforeEach
    void setUp() {
        service = new AdminBagPriceTierService(tierRepository);
        when(tierRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAcceptsValidTier() {
        when(tierRepository.existsByCategoryAndBagSize(StoreCategory.BAKERY, BagSize.STANDARD)).thenReturn(false);

        assertDoesNotThrow(() -> service.create(validRequest()));

        verify(tierRepository).save(any(BagPriceTier.class));
    }

    @Test
    void createRejectsDuplicateCategoryAndSize() {
        when(tierRepository.existsByCategoryAndBagSize(StoreCategory.BAKERY, BagSize.STANDARD)).thenReturn(true);

        assertThrows(ApiException.class, () -> service.create(validRequest()));

        verify(tierRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidDynamicRange() {
        BagPriceTierRequest request = validRequest();
        request.setDynamicMinPrice(BigDecimal.valueOf(50000));
        request.setBaseSalePrice(BigDecimal.valueOf(39000));

        assertThrows(ApiException.class, () -> service.create(request));

        verify(tierRepository, never()).save(any());
    }

    @Test
    void deactivateTurnsTierOff() {
        BagPriceTier tier = tier();
        when(tierRepository.findById(tier.getId())).thenReturn(Optional.of(tier));

        service.deactivate(tier.getId());

        verify(tierRepository).save(argThat(saved -> !saved.isActive()));
    }

    private BagPriceTierRequest validRequest() {
        BagPriceTierRequest request = new BagPriceTierRequest();
        request.setCategory(StoreCategory.BAKERY);
        request.setBagSize(BagSize.STANDARD);
        request.setMinimumValue(BigDecimal.valueOf(100000));
        request.setBaseSalePrice(BigDecimal.valueOf(39000));
        request.setDynamicMinPrice(BigDecimal.valueOf(35000));
        request.setDynamicMaxPrice(BigDecimal.valueOf(45000));
        request.setPlatformFee(BigDecimal.valueOf(4000));
        request.setActive(true);
        return request;
    }

    private BagPriceTier tier() {
        BagPriceTier tier = BagPriceTier.builder()
                .category(StoreCategory.BAKERY)
                .bagSize(BagSize.STANDARD)
                .minimumValue(BigDecimal.valueOf(100000))
                .baseSalePrice(BigDecimal.valueOf(39000))
                .dynamicMinPrice(BigDecimal.valueOf(35000))
                .dynamicMaxPrice(BigDecimal.valueOf(45000))
                .platformFee(BigDecimal.valueOf(4000))
                .active(true)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(tier, "id", java.util.UUID.randomUUID());
        return tier;
    }
}
