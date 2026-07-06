package com.LastBite.modules.bag.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.bag.dto.response.PublicBagDetailResponse;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.enums.BagType;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.BagDiscoveryProjection;
import com.LastBite.modules.bag.service.BagDiscoveryServicePort;
import com.LastBite.modules.discovery.service.DiscoveryRankingService;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.user.entity.UserDiscoveryPreference;
import com.LastBite.modules.user.enums.CollectionTimeSlot;
import com.LastBite.modules.user.enums.PreferredDiet;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BagDiscoveryService implements BagDiscoveryServicePort {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_QUERY_LIMIT = 300;
    private static final double DEFAULT_RADIUS_KM = 5.0;
    private static final int DIET_MATCH_SCORE = 100;
    private static final int DIET_COMPATIBLE_SCORE = 80;
    private static final int COLLECTION_TIME_MATCH_SCORE = 60;

    private final BagDailyStockRepository stockRepository;
    private final UserDiscoveryPreferenceRepository discoveryPreferenceRepository;
    private final BagDiscoveryMapper mapper;
    private final DiscoveryRankingService rankingService;
    private final Clock clock;

    @Cacheable(value = "bag-discovery",
            key = "'today:' + #userId + ':' + #lat + ':' + #lng + ':' + #radiusKm + ':' + #dietType + ':' + #bagType + ':' + #sort + ':' + #limit")
    public List<PublicBagSummaryResponse> today(UUID userId, Double lat, Double lng, Double radiusKm, DietType dietType,
                                                BagType bagType, String sort, Integer limit) {
        return discover(userId, lat, lng, radiusKm, null, dietType, bagType, null, sort, limit);
    }

    @Cacheable(value = "bag-discovery",
            key = "'nearby:' + #userId + ':' + #lat + ':' + #lng + ':' + #radiusKm + ':' + #category + ':' + #dietType + ':' + #bagType + ':' + #district + ':' + #sort + ':' + #limit")
    public List<PublicBagSummaryResponse> discover(UUID userId, Double lat, Double lng, Double radiusKm, StoreCategory category,
                                                    DietType dietType, BagType bagType, String district,
                                                    String sort, Integer limit) {
        return discoverInternal(userId, null, lat, lng, radiusKm, category, dietType, bagType, district, sort, limit);
    }

    @Cacheable(value = "bag-discovery",
            key = "'search:' + #userId + ':' + #keyword + ':' + #lat + ':' + #lng + ':' + #radiusKm + ':' + #category + ':' + #dietType + ':' + #bagType + ':' + #district + ':' + #sort + ':' + #limit")
    public List<PublicBagSummaryResponse> search(UUID userId, String keyword, Double lat, Double lng, Double radiusKm,
                                                 StoreCategory category, DietType dietType, BagType bagType,
                                                 String district, String sort, Integer limit) {
        return discoverInternal(userId, keyword, lat, lng, radiusKm, category, dietType, bagType, district, sort, limit);
    }

    @Cacheable(value = "bag-detail", key = "#bagId + ':' + #userId + ':' + #lat + ':' + #lng")
    public PublicBagDetailResponse detail(UUID bagId, UUID userId, Double lat, Double lng) {
        UserDiscoveryPreference preference = loadPreference(userId);
        LocationContext location = resolveLocation(preference, lat, lng, null);
        BagDiscoveryProjection row = stockRepository.findPublicBagDetail(bagId, LocalDate.now(clock), LocalTime.now(clock),
                        location.lat(), location.lng())
                .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
        return mapper.toDetail(row, userId);
    }

    @Cacheable(value = "store-bags", key = "#storeId + ':' + #limit")
    public List<PublicBagSummaryResponse> storeBags(UUID storeId, Integer limit) {
        return stockRepository.findPublicStoreBags(storeId, LocalDate.now(clock), LocalTime.now(clock), normalizeLimit(limit))
                .stream()
                .map(row -> mapper.toSummary(row, null))
                .sorted(Comparator.comparing(PublicBagSummaryResponse::isSoldOut)
                        .thenComparing(PublicBagSummaryResponse::getPickupStartTime))
                .toList();
    }

    private List<PublicBagSummaryResponse> discoverInternal(UUID userId, String keyword, Double lat, Double lng,
                                                            Double radiusKm, StoreCategory category, DietType dietType,
                                                            BagType bagType, String district, String sort,
                                                            Integer limit) {
        String normalizedSort = normalizeSort(sort);
        int normalizedLimit = normalizeLimit(limit);
        UserDiscoveryPreference preference = loadPreference(userId);
        boolean hasPreferenceRanking = hasPreferenceRanking(preference, dietType != null);
        int queryLimit = needsWideCandidatePool(normalizedSort, hasPreferenceRanking, keyword)
                ? Math.min(MAX_QUERY_LIMIT, Math.max(normalizedLimit * 5, DEFAULT_LIMIT))
                : normalizedLimit;

        LocationContext location = resolveLocation(preference, lat, lng, radiusKm);
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        List<BagDiscoveryProjection> rows = fetchCandidates(today, now, location, category, dietType, bagType,
                district, keyword, queryLimit);

        List<PublicBagSummaryResponse> summaries = rows.stream()
                .map(row -> mapper.toSummary(row, userId))
                .toList();
        return sortSummaries(summaries, normalizedSort, normalizedLimit, preference, dietType != null,
                location.radiusKm());
    }

    private List<BagDiscoveryProjection> fetchCandidates(LocalDate date, LocalTime now, LocationContext location,
                                                         StoreCategory category, DietType dietType, BagType bagType,
                                                         String district, String keyword, int limit) {
        String categoryValue = category == null ? null : category.name();
        String dietTypeValue = dietType == null ? null : dietType.name();
        String bagTypeValue = bagType == null ? null : bagType.name();
        String normalizedDistrict = normalize(district);
        String keywordPattern = keywordPattern(keyword);
        if (location.hasLocation()) {
            return stockRepository.findDiscoveryCandidatesWithLocation(date, now, location.lat(), location.lng(),
                    location.radiusKm(), categoryValue, dietTypeValue, bagTypeValue, normalizedDistrict,
                    keywordPattern, limit);
        }
        return stockRepository.findDiscoveryCandidatesWithoutLocation(date, now, categoryValue, dietTypeValue,
                bagTypeValue, normalizedDistrict, keywordPattern, limit);
    }

    private LocationContext resolveLocation(UserDiscoveryPreference preference, Double lat, Double lng, Double radiusKm) {
        boolean hasLat = lat != null;
        boolean hasLng = lng != null;
        if (hasLat != hasLng) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Can truyen du ca lat va lng khi tim theo vi tri");
        }
        if (radiusKm != null && (radiusKm <= 0 || radiusKm > 50)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Ban kinh tim kiem phai lon hon 0 va toi da 50 km");
        }
        if (hasLat) {
            return new LocationContext(true, lat, lng, radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm);
        }
        if (hasSavedLocation(preference)) {
            return new LocationContext(true, preference.getDefaultLat(), preference.getDefaultLng(),
                    preference.getDefaultRadiusKm());
        }
        return new LocationContext(false, null, null, radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "pickup_time";
        String value = sort.trim().toLowerCase();
        if (!List.of("pickup_time", "distance", "price", "rating", "relevance").contains(value)) {
            throw new ApiException(ErrorCode.INVALID_SORT_FIELD,
                    "Sort chi ho tro pickup_time, distance, price, rating hoac relevance");
        }
        return value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        if (limit < 1 || limit > 100) {
            throw new ApiException(ErrorCode.INVALID_PAGINATION, "Limit phai tu 1 den 100");
        }
        return limit;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String keywordPattern(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : "%" + normalized.toLowerCase() + "%";
    }

    private boolean needsWideCandidatePool(String sort, boolean hasPreferenceRanking, String keyword) {
        return hasPreferenceRanking
                || keywordPattern(keyword) != null
                || List.of("price", "rating", "relevance").contains(sort);
    }

    private List<PublicBagSummaryResponse> sortSummaries(List<PublicBagSummaryResponse> values, String sort, int limit,
                                                         UserDiscoveryPreference preference, boolean explicitDietFilter,
                                                         double radiusKm) {
        Comparator<PublicBagSummaryResponse> comparator = Comparator.comparing(PublicBagSummaryResponse::isSoldOut);
        Comparator<PublicBagSummaryResponse> preferenceComparator = Comparator
                .comparingInt((PublicBagSummaryResponse value) -> preferenceScore(value, preference, explicitDietFilter))
                .reversed();
        comparator = switch (sort) {
            case "distance" -> comparator.thenComparing(
                    PublicBagSummaryResponse::getDistanceKm,
                    Comparator.nullsLast(Double::compareTo)).thenComparing(preferenceComparator);
            case "price" -> comparator.thenComparing(PublicBagSummaryResponse::getCurrentSalePrice)
                    .thenComparing(preferenceComparator);
            case "rating" -> comparator
                    .thenComparing((PublicBagSummaryResponse value) -> doubleOrZero(value.getStoreAvgRating()), Comparator.reverseOrder())
                    .thenComparing((PublicBagSummaryResponse value) -> intOrZero(value.getStoreTotalRatings()), Comparator.reverseOrder())
                    .thenComparing(preferenceComparator);
            case "relevance" -> comparator
                    .thenComparing((PublicBagSummaryResponse value) -> rankingService.score(value, radiusKm).finalScore(), Comparator.reverseOrder())
                    .thenComparing(preferenceComparator);
            default -> comparator.thenComparing(preferenceComparator)
                    .thenComparing(PublicBagSummaryResponse::getPickupStartTime);
        };
        return values.stream()
                .sorted(comparator.thenComparing(PublicBagSummaryResponse::getPickupStartTime))
                .limit(limit)
                .toList();
    }

    private UserDiscoveryPreference loadPreference(UUID userId) {
        if (userId == null) return null;
        return discoveryPreferenceRepository.findByUserId(userId).orElse(null);
    }

    private boolean hasSavedLocation(UserDiscoveryPreference preference) {
        return preference != null
                && preference.getDefaultLat() != null
                && preference.getDefaultLng() != null
                && preference.getDefaultRadiusKm() != null;
    }

    private boolean hasPreferenceRanking(UserDiscoveryPreference preference, boolean explicitDietFilter) {
        if (preference == null) return false;
        boolean hasDietPreference = !explicitDietFilter
                && preference.getPreferredDiet() != null
                && preference.getPreferredDiet() != PreferredDiet.EAT_EVERYTHING
                && preference.getPreferredDiet() != PreferredDiet.NOT_SPECIFIED;
        boolean hasCollectionTimePreference = preference.getPreferredCollectionTimes() != null
                && !preference.getPreferredCollectionTimes().isEmpty();
        return hasDietPreference || hasCollectionTimePreference;
    }

    private int preferenceScore(PublicBagSummaryResponse value, UserDiscoveryPreference preference,
                                boolean explicitDietFilter) {
        if (preference == null) return 0;
        int score = 0;
        if (!explicitDietFilter) {
            score += dietPreferenceScore(value.getDietType(), preference.getPreferredDiet());
        }
        if (overlapsPreferredCollectionTime(value, preference)) {
            score += COLLECTION_TIME_MATCH_SCORE;
        }
        return score;
    }

    private int dietPreferenceScore(DietType bagDietType, PreferredDiet preferredDiet) {
        if (bagDietType == null || preferredDiet == null) return 0;
        return switch (preferredDiet) {
            case VEGETARIAN -> {
                if (bagDietType == DietType.VEGETARIAN) yield DIET_MATCH_SCORE;
                if (bagDietType == DietType.VEGAN) yield DIET_COMPATIBLE_SCORE;
                yield 0;
            }
            case VEGAN -> bagDietType == DietType.VEGAN ? DIET_MATCH_SCORE : 0;
            case EAT_EVERYTHING, NOT_SPECIFIED -> 0;
        };
    }

    private boolean overlapsPreferredCollectionTime(PublicBagSummaryResponse value,
                                                    UserDiscoveryPreference preference) {
        if (preference.getPreferredCollectionTimes() == null
                || preference.getPreferredCollectionTimes().isEmpty()
                || value.getPickupStartTime() == null
                || value.getPickupEndTime() == null) {
            return false;
        }
        return preference.getPreferredCollectionTimes().stream()
                .anyMatch(slot -> overlapsSlot(value.getPickupStartTime(), value.getPickupEndTime(), slot));
    }

    private boolean overlapsSlot(LocalTime start, LocalTime end, CollectionTimeSlot slot) {
        int startSecond = start.toSecondOfDay();
        int endSecond = end.equals(LocalTime.MIDNIGHT) ? 24 * 60 * 60 : end.toSecondOfDay();
        if (endSecond <= startSecond) {
            endSecond += 24 * 60 * 60;
        }
        int slotStart = slotStartSecond(slot);
        int slotEnd = slotEndSecond(slot);
        return startSecond < slotEnd && endSecond > slotStart;
    }

    private int slotStartSecond(CollectionTimeSlot slot) {
        return switch (slot) {
            case EARLY_MORNING -> 6 * 60 * 60;
            case LATE_MORNING -> 9 * 60 * 60;
            case MIDDAY -> 12 * 60 * 60;
            case AFTERNOON -> 15 * 60 * 60;
            case EVENING -> 18 * 60 * 60;
            case LATE_NIGHT -> 21 * 60 * 60;
        };
    }

    private int slotEndSecond(CollectionTimeSlot slot) {
        return switch (slot) {
            case EARLY_MORNING -> 9 * 60 * 60;
            case LATE_MORNING -> 12 * 60 * 60;
            case MIDDAY -> 15 * 60 * 60;
            case AFTERNOON -> 18 * 60 * 60;
            case EVENING -> 21 * 60 * 60;
            case LATE_NIGHT -> 24 * 60 * 60;
        };
    }

    private double doubleOrZero(Double value) {
        return value == null ? 0 : value;
    }

    private int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record LocationContext(boolean hasLocation, Double lat, Double lng, double radiusKm) {
    }
}
