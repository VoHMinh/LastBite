package com.LastBite.common.config;

import com.LastBite.modules.auth.dto.response.UserResponse;
import com.LastBite.modules.auth.enums.AccountType;
import com.LastBite.modules.auth.enums.AuthProvider;
import com.LastBite.modules.auth.enums.UserStatus;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.enums.BagSize;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.store.dto.response.StoreResponse;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.user.dto.response.AddressResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CacheConfigTest {

    private final RedisSerializer<Object> serializer = new CacheConfig().redisCacheValueSerializer();

    @Test
    void userAddressesCacheRoundTripsImmutableListOfDto() {
        List<AddressResponse> value = Stream.of(AddressResponse.builder()
                        .id(UUID.randomUUID())
                        .label("HOME")
                        .fullAddress("123 Main St")
                        .lat(10.1)
                        .lng(106.2)
                        .isDefault(true)
                        .createdAt(Instant.parse("2026-06-19T08:00:00Z"))
                        .build())
                .toList();

        Object restored = serializer.deserialize(serializer.serialize(value));

        assertInstanceOf(List.class, restored);
        Object first = ((List<?>) restored).getFirst();
        assertInstanceOf(AddressResponse.class, first);
        assertEquals("HOME", ((AddressResponse) first).getLabel());
    }

    @Test
    void userProfileCacheRoundTripsDtoWithRolesAndInstant() {
        UserResponse value = UserResponse.builder()
                .id(UUID.randomUUID())
                .email("customer@test.com")
                .fullName("Customer")
                .accountType(AccountType.PLATFORM)
                .roles(List.of("CUSTOMER"))
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .phoneVerified(false)
                .createdAt(Instant.parse("2026-06-19T08:00:00Z"))
                .build();

        Object restored = serializer.deserialize(serializer.serialize(value));

        assertInstanceOf(UserResponse.class, restored);
        assertEquals("customer@test.com", ((UserResponse) restored).getEmail());
    }

    @Test
    void bagDiscoveryCacheRoundTripsListWithDateTimeAndMoneyFields() {
        List<PublicBagSummaryResponse> value = List.of(PublicBagSummaryResponse.builder()
                .bagId(UUID.randomUUID())
                .storeId(UUID.randomUUID())
                .storeName("Bakery")
                .storeSlug("bakery")
                .bagType(BagType.BREAD)
                .dietType(DietType.VEGETARIAN)
                .category(StoreCategory.BAKERY)
                .bagSize(BagSize.STANDARD)
                .photos(List.of("https://cdn.test/bag.jpg"))
                .minimumValue(BigDecimal.valueOf(100000))
                .baseSalePrice(BigDecimal.valueOf(39000))
                .currentSalePrice(BigDecimal.valueOf(35000))
                .savingsAmount(BigDecimal.valueOf(65000))
                .dynamicMinPrice(BigDecimal.valueOf(30000))
                .dynamicMaxPrice(BigDecimal.valueOf(45000))
                .platformFee(BigDecimal.valueOf(4000))
                .stockDate(LocalDate.of(2026, 6, 19))
                .pickupStartTime(LocalTime.of(18, 0))
                .pickupEndTime(LocalTime.of(20, 0))
                .quantity(3)
                .available(2)
                .build());

        Object restored = serializer.deserialize(serializer.serialize(value));

        assertInstanceOf(List.class, restored);
        Object first = ((List<?>) restored).getFirst();
        assertInstanceOf(PublicBagSummaryResponse.class, first);
        assertEquals(LocalTime.of(18, 0), ((PublicBagSummaryResponse) first).getPickupStartTime());
    }

    @Test
    void storeListCacheRoundTripsPageResponseShape() {
        Page<StoreResponse> value = new PageImpl<>(
                List.of(StoreResponse.builder()
                        .id(UUID.randomUUID())
                        .name("Store")
                        .slug("store")
                        .category(StoreCategory.RESTAURANT)
                        .status(StoreStatus.ACTIVE)
                        .verificationStatus(VerificationStatus.VERIFIED)
                        .galleryImageUrls(List.of("https://cdn.test/store.jpg"))
                        .createdAt(Instant.parse("2026-06-19T08:00:00Z"))
                        .build()),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                1);

        Object restored = serializer.deserialize(serializer.serialize(value));

        assertInstanceOf(Page.class, restored);
        Page<?> page = (Page<?>) restored;
        assertEquals(1, page.getTotalElements());
        assertInstanceOf(StoreResponse.class, page.getContent().getFirst());
    }
}
