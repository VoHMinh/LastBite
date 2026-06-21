package com.LastBite.modules.discovery.service;

public record RankingScore(
        double finalScore,
        double distanceScore,
        double urgencyScore,
        double discountScore,
        double ratingScore,
        double availabilityScore
) {
}
