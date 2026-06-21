package com.LastBite.modules.discovery.service;

import java.util.Map;

public record DiscoveryRankingConfig(
        double weightDistance,
        double weightUrgency,
        double weightDiscount,
        double weightRating,
        double weightAvailability,
        double maxRadiusKm,
        int minReachableMinutes,
        int urgencyWindowMinutes,
        int minReviewsThreshold,
        double defaultNeutralRatingScore,
        int availabilityNormalizeCap,
        double favoriteStoreBoost,
        double categoryHistoryBoost
) {

    public static DiscoveryRankingConfig defaults() {
        return new DiscoveryRankingConfig(
                0.30,
                0.20,
                0.20,
                0.15,
                0.15,
                5.0,
                15,
                120,
                5,
                0.8,
                10,
                0.20,
                0.15);
    }

    @SuppressWarnings("unchecked")
    public static DiscoveryRankingConfig from(Map<String, Object> value) {
        DiscoveryRankingConfig defaults = defaults();
        if (value == null || value.isEmpty()) {
            return defaults;
        }
        Map<String, Object> weights = value.get("weights") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        return new DiscoveryRankingConfig(
                number(weights, "distance", defaults.weightDistance()),
                number(weights, "urgency", defaults.weightUrgency()),
                number(weights, "discount", defaults.weightDiscount()),
                number(weights, "rating", defaults.weightRating()),
                number(weights, "availability", defaults.weightAvailability()),
                number(value, "maxRadiusKm", defaults.maxRadiusKm()),
                integer(value, "minReachableMinutes", defaults.minReachableMinutes()),
                integer(value, "urgencyWindowMinutes", defaults.urgencyWindowMinutes()),
                integer(value, "minReviewsThreshold", defaults.minReviewsThreshold()),
                number(value, "defaultNeutralRatingScore", defaults.defaultNeutralRatingScore()),
                integer(value, "availabilityNormalizeCap", defaults.availabilityNormalizeCap()),
                number(value, "favoriteStoreBoost", defaults.favoriteStoreBoost()),
                number(value, "categoryHistoryBoost", defaults.categoryHistoryBoost()));
    }

    private static double number(Map<String, Object> value, String key, double fallback) {
        Object raw = value.get(key);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static int integer(Map<String, Object> value, String key, int fallback) {
        Object raw = value.get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
