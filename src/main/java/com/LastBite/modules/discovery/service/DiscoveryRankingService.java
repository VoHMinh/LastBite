package com.LastBite.modules.discovery.service;

import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.discovery.repository.PlatformConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DiscoveryRankingService {

    private static final String RANKING_CONFIG_KEY = "discovery.ranking";

    private final PlatformConfigRepository platformConfigRepository;
    private final Clock clock;

    @Cacheable(value = "discovery-config", key = "'ranking'")
    public DiscoveryRankingConfig config() {
        return platformConfigRepository.findById(RANKING_CONFIG_KEY)
                .map(config -> DiscoveryRankingConfig.from(config.getConfigValue()))
                .orElseGet(DiscoveryRankingConfig::defaults);
    }

    public RankingScore score(PublicBagSummaryResponse bag, Double radiusKm) {
        DiscoveryRankingConfig config = config();
        double distance = distanceScore(bag.getDistanceKm(), radiusKm == null ? config.maxRadiusKm() : radiusKm);
        double urgency = urgencyScore(bag, config);
        double discount = discountScore(bag);
        double rating = ratingScore(bag, config);
        double availability = availabilityScore(bag.getAvailable(), config.availabilityNormalizeCap());
        double finalScore = config.weightDistance() * distance
                + config.weightUrgency() * urgency
                + config.weightDiscount() * discount
                + config.weightRating() * rating
                + config.weightAvailability() * availability;
        return new RankingScore(clamp(finalScore), distance, urgency, discount, rating, availability);
    }

    public double urgencyScore(PublicBagSummaryResponse bag, DiscoveryRankingConfig config) {
        if (bag.getStockDate() == null || bag.getPickupEndTime() == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime pickupEnd = bag.getStockDate().atTime(bag.getPickupEndTime());
        long minutesLeft = Duration.between(now, pickupEnd).toMinutes();
        if (minutesLeft < config.minReachableMinutes()) {
            return 0;
        }
        double raw = 1.0 - ((double) minutesLeft - config.minReachableMinutes())
                / Math.max(1, config.urgencyWindowMinutes());
        return clamp(raw);
    }

    public double distanceScore(Double distanceKm, double maxRadiusKm) {
        if (distanceKm == null || maxRadiusKm <= 0) {
            return 0;
        }
        return clamp(1.0 - distanceKm / maxRadiusKm);
    }

    public double discountScore(PublicBagSummaryResponse bag) {
        if (bag.getMinimumValue() == null || bag.getCurrentSalePrice() == null
                || bag.getMinimumValue().signum() <= 0) {
            return 0;
        }
        BigDecimal savings = bag.getMinimumValue().subtract(bag.getCurrentSalePrice()).max(BigDecimal.ZERO);
        return clamp(savings.divide(bag.getMinimumValue(), 6, RoundingMode.HALF_UP).doubleValue());
    }

    public double ratingScore(PublicBagSummaryResponse bag, DiscoveryRankingConfig config) {
        if (bag.getStoreTotalRatings() == null || bag.getStoreTotalRatings() < config.minReviewsThreshold()) {
            return clamp(config.defaultNeutralRatingScore());
        }
        if (bag.getStoreAvgRating() == null) {
            return 0;
        }
        return clamp(bag.getStoreAvgRating() / 5.0);
    }

    public double availabilityScore(int available, int normalizeCap) {
        if (normalizeCap <= 0 || available <= 0) {
            return 0;
        }
        return clamp((double) available / normalizeCap);
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }
}
