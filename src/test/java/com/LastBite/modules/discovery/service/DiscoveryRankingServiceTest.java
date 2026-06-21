package com.LastBite.modules.discovery.service;

import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.discovery.repository.PlatformConfigRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DiscoveryRankingServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));
    private final DiscoveryRankingService service = new DiscoveryRankingService(
            mock(PlatformConfigRepository.class), clock);

    @Test
    void scoreNormalizesAllComponentsAndAppliesWeights() {
        PublicBagSummaryResponse bag = bag(LocalTime.of(11, 15), 2.5, 5, 4.5, 12,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(60000));

        RankingScore score = service.score(bag, 5.0);

        assertEquals(0.5, score.distanceScore(), 0.0001);
        assertEquals(0.5, score.urgencyScore(), 0.0001);
        assertEquals(0.4, score.discountScore(), 0.0001);
        assertEquals(0.9, score.ratingScore(), 0.0001);
        assertEquals(0.5, score.availabilityScore(), 0.0001);
        assertEquals(0.54, score.finalScore(), 0.0001);
    }

    @Test
    void ratingUsesNeutralScoreForColdStartStores() {
        PublicBagSummaryResponse bag = bag(LocalTime.of(11, 15), 1.0, 3, 5.0, 1,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(60000));

        RankingScore score = service.score(bag, 5.0);

        assertEquals(0.8, score.ratingScore(), 0.0001);
    }

    @Test
    void urgencyIsZeroWhenPickupEndIsTooSoonToReach() {
        PublicBagSummaryResponse bag = bag(LocalTime.of(10, 10), 1.0, 3, 4.5, 10,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(60000));

        RankingScore score = service.score(bag, 5.0);

        assertEquals(0, score.urgencyScore(), 0.0001);
    }

    @Test
    void availabilityRewardsMoreRemainingInventory() {
        PublicBagSummaryResponse low = bag(LocalTime.of(11, 15), 1.0, 1, 4.5, 10,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(60000));
        PublicBagSummaryResponse high = bag(LocalTime.of(11, 15), 1.0, 9, 4.5, 10,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(60000));

        assertTrue(service.score(high, 5.0).availabilityScore() > service.score(low, 5.0).availabilityScore());
        assertTrue(service.score(high, 5.0).finalScore() > service.score(low, 5.0).finalScore());
    }

    private PublicBagSummaryResponse bag(LocalTime pickupEnd, Double distanceKm, int available,
                                         Double rating, int totalRatings,
                                         BigDecimal minimumValue, BigDecimal currentSalePrice) {
        return PublicBagSummaryResponse.builder()
                .stockDate(LocalDate.of(2026, 5, 25))
                .pickupEndTime(pickupEnd)
                .distanceKm(distanceKm)
                .available(available)
                .storeAvgRating(rating)
                .storeTotalRatings(totalRatings)
                .minimumValue(minimumValue)
                .currentSalePrice(currentSalePrice)
                .build();
    }
}
