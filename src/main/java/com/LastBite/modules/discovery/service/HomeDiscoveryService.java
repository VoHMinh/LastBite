package com.LastBite.modules.discovery.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.bag.dto.response.PublicBagSummaryResponse;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.BagDiscoveryProjection;
import com.LastBite.modules.bag.service.impl.BagDiscoveryMapper;
import com.LastBite.modules.discovery.dto.response.HomeDiscoveryCollectionResponse;
import com.LastBite.modules.discovery.entity.DiscoveryCollection;
import com.LastBite.modules.discovery.entity.DiscoveryCollectionItem;
import com.LastBite.modules.discovery.enums.DiscoveryCollectionType;
import com.LastBite.modules.discovery.enums.DiscoveryRule;
import com.LastBite.modules.discovery.enums.DiscoverySort;
import com.LastBite.modules.discovery.repository.DiscoveryCollectionItemRepository;
import com.LastBite.modules.discovery.repository.DiscoveryCollectionRepository;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.store.enums.StoreCategory;
import com.LastBite.modules.user.entity.UserDiscoveryPreference;
import com.LastBite.modules.user.repository.UserDiscoveryPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeDiscoveryService {

    private static final int CANDIDATE_LIMIT = 300;
    private static final double DEFAULT_RADIUS_KM = 5.0;

    private final DiscoveryCollectionRepository collectionRepository;
    private final DiscoveryCollectionItemRepository itemRepository;
    private final BagDailyStockRepository stockRepository;
    private final UserDiscoveryPreferenceRepository preferenceRepository;
    private final OrderRepository orderRepository;
    private final BagDiscoveryMapper mapper;
    private final DiscoveryRankingService rankingService;
    private final Clock clock;

    @Cacheable(value = "home-discovery", key = "#userId + ':' + #lat + ':' + #lng + ':' + #radiusKm")
    public List<HomeDiscoveryCollectionResponse> discover(UUID userId, Double lat, Double lng, Double radiusKm) {
        UserDiscoveryPreference preference = loadPreference(userId);
        LocationContext location = resolveLocation(preference, lat, lng, radiusKm);
        List<DiscoveryCandidate> candidates = loadCandidates(userId, location);
        DiscoveryRankingConfig config = rankingService.config();
        Set<StoreCategory> purchasedCategories = userId == null
                ? Set.of()
                : new HashSet<>(orderRepository.findPurchasedCategoriesByUser(userId));

        return collectionRepository.findByActiveTrueOrderByDisplayOrderAscSlugAsc().stream()
                .map(collection -> toHomeCollection(collection, candidates, location, config, userId, purchasedCategories))
                .filter(response -> response != null)
                .toList();
    }

    private HomeDiscoveryCollectionResponse toHomeCollection(DiscoveryCollection collection,
                                                             List<DiscoveryCandidate> candidates,
                                                             LocationContext location,
                                                             DiscoveryRankingConfig config,
                                                             UUID userId,
                                                             Set<StoreCategory> purchasedCategories) {
        List<DiscoveryCandidate> matching = candidatesFor(collection, candidates, location, config,
                userId, purchasedCategories);
        List<PublicBagSummaryResponse> items = matching.stream()
                .limit(collection.getMaxItems())
                .map(DiscoveryCandidate::summary)
                .toList();
        if (items.size() < collection.getMinItemsToDisplay()) {
            return null;
        }
        return HomeDiscoveryCollectionResponse.builder()
                .id(collection.getId())
                .slug(collection.getSlug())
                .title(collection.getTitle())
                .type(collection.getType())
                .displayOrder(collection.getDisplayOrder())
                .items(items)
                .build();
    }


    private List<DiscoveryCandidate> candidatesFor(DiscoveryCollection collection,
                                                   List<DiscoveryCandidate> candidates,
                                                   LocationContext location,
                                                   DiscoveryRankingConfig config,
                                                   UUID userId,
                                                   Set<StoreCategory> purchasedCategories) {
        RuleDefinition definition = RuleDefinition.from(collection.getRuleDefinition());
        if (collection.getType() == DiscoveryCollectionType.CURATED_MANUAL) {
            return manualCandidates(collection, candidates);
        }
        if (collection.getType() == DiscoveryCollectionType.PERSONALIZED
                || definition.rule() == DiscoveryRule.RECOMMENDED_FOR_YOU) {
            if (userId == null) {
                return List.of();
            }
            return candidates.stream()
                    .sorted(Comparator.comparing(
                            (DiscoveryCandidate candidate) -> personalizedScore(candidate, config, purchasedCategories),
                            Comparator.reverseOrder()))
                    .toList();
        }
        Predicate<DiscoveryCandidate> predicate = predicateFor(definition, location, config);
        return sortCandidates(candidates.stream().filter(predicate).toList(), definition.sort());
    }

    private Predicate<DiscoveryCandidate> predicateFor(RuleDefinition definition,
                                                       LocationContext location,
                                                       DiscoveryRankingConfig config) {
        return switch (definition.rule()) {
            case NEAR_YOU -> candidate -> location.hasLocation()
                    && candidate.summary().getDistanceKm() != null
                    && candidate.summary().getDistanceKm() <= numberParam(definition, "maxDistanceKm", location.radiusKm());
            case LAST_CHANCE -> candidate -> rankingService.urgencyScore(candidate.summary(), config) > 0;
            case BIG_DISCOUNT -> candidate -> candidate.summary().getCurrentDiscountPercent()
                    >= integerParam(definition, "minDiscountPercent", 35);
            case UNDER_PRICE -> candidate -> candidate.summary().getCurrentSalePrice() != null
                    && candidate.summary().getCurrentSalePrice().compareTo(
                    BigDecimal.valueOf(numberParam(definition, "maxPrice", 30000))) <= 0;
            case NEW_STORES -> candidate -> candidate.row().getStoreCreatedAt() != null
                    && candidate.row().getStoreCreatedAt().isAfter(Instant.now(clock)
                    .minus(Duration.ofDays(integerParam(definition, "days", 30))));
            case TOP_RATED -> candidate -> doubleOrZero(candidate.summary().getStoreAvgRating())
                    >= numberParam(definition, "minRating", 4.0)
                    && intOrZero(candidate.summary().getStoreTotalRatings())
                    >= integerParam(definition, "minReviews", config.minReviewsThreshold());
            case BESTSELLER_TODAY -> candidate -> intOrZero(candidate.row().getOrdersTodayCount()) > 0;
            case RECOMMENDED_FOR_YOU, MANUAL -> candidate -> true;
        };
    }

    private List<DiscoveryCandidate> sortCandidates(List<DiscoveryCandidate> values, DiscoverySort sort) {
        Comparator<DiscoveryCandidate> soldOutLast = Comparator.comparing(
                candidate -> candidate.summary().isSoldOut());
        Comparator<DiscoveryCandidate> comparator = switch (sort) {
            case DISTANCE_ASC -> soldOutLast.thenComparing(
                    candidate -> candidate.summary().getDistanceKm(),
                    Comparator.nullsLast(Double::compareTo));
            case DISCOUNT_DESC -> soldOutLast.thenComparing(
                    candidate -> candidate.summary().getCurrentDiscountPercent(),
                    Comparator.reverseOrder());
            case STORE_CREATED_DESC -> soldOutLast.thenComparing(
                    candidate -> candidate.row().getStoreCreatedAt(),
                    Comparator.nullsLast(Comparator.reverseOrder()));
            case RATING_DESC -> soldOutLast
                    .thenComparing(candidate -> doubleOrZero(candidate.summary().getStoreAvgRating()),
                            Comparator.reverseOrder())
                    .thenComparing(candidate -> intOrZero(candidate.summary().getStoreTotalRatings()),
                            Comparator.reverseOrder());
            case ORDERS_TODAY_DESC -> soldOutLast.thenComparing(
                    candidate -> intOrZero(candidate.row().getOrdersTodayCount()),
                    Comparator.reverseOrder());
            case PERSONALIZED_DESC -> Comparator.comparing(
                    DiscoveryCandidate::baseScore, Comparator.reverseOrder());
            case PINNED_THEN_RANKING, RANKING_DESC -> soldOutLast.thenComparing(
                    DiscoveryCandidate::baseScore, Comparator.reverseOrder());
        };
        return values.stream()
                .sorted(comparator.thenComparing(candidate -> candidate.summary().getPickupStartTime()))
                .toList();
    }

    private List<DiscoveryCandidate> manualCandidates(DiscoveryCollection collection,
                                                      List<DiscoveryCandidate> candidates) {
        Map<UUID, DiscoveryCandidate> byBagId = new LinkedHashMap<>();
        candidates.forEach(candidate -> byBagId.put(candidate.summary().getBagId(), candidate));
        return itemRepository.findByCollectionIdOrderByPinnedOrderAscAddedAtAsc(collection.getId()).stream()
                .map(item -> manualCandidate(item, byBagId.get(item.getBag().getId())))
                .filter(candidate -> candidate.value() != null)
                .sorted(Comparator
                        .comparing(ManualCandidate::pinnedOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(candidate -> candidate.value().baseScore(), Comparator.reverseOrder()))
                .map(ManualCandidate::value)
                .toList();
    }

    private ManualCandidate manualCandidate(DiscoveryCollectionItem item, DiscoveryCandidate candidate) {
        return new ManualCandidate(item.getPinnedOrder(), candidate);
    }

    private double personalizedScore(DiscoveryCandidate candidate, DiscoveryRankingConfig config,
                                     Set<StoreCategory> purchasedCategories) {
        double score = candidate.baseScore();
        if (candidate.summary().isFavoriteStore()) {
            score += config.favoriteStoreBoost();
        }
        if (purchasedCategories.contains(candidate.summary().getCategory())) {
            score += config.categoryHistoryBoost();
        }
        return score;
    }

    private List<DiscoveryCandidate> loadCandidates(UUID userId, LocationContext location) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);
        List<BagDiscoveryProjection> rows = location.hasLocation()
                ? stockRepository.findDiscoveryCandidatesWithLocation(today, now, location.lat(), location.lng(),
                location.radiusKm(), null, null, null, null, null, CANDIDATE_LIMIT)
                : stockRepository.findDiscoveryCandidatesWithoutLocation(today, now, null, null, null, null, null,
                CANDIDATE_LIMIT);
        return rows.stream()
                .map(row -> {
                    PublicBagSummaryResponse summary = mapper.toSummary(row, userId);
                    return new DiscoveryCandidate(row, summary,
                            rankingService.score(summary, location.radiusKm()).finalScore());
                })
                .toList();
    }

    private LocationContext resolveLocation(UserDiscoveryPreference preference, Double lat, Double lng, Double radiusKm) {
        boolean hasLat = lat != null;
        boolean hasLng = lng != null;
        if (hasLat != hasLng) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Can truyen du ca lat va lng khi lay home discovery");
        }
        if (radiusKm != null && (radiusKm <= 0 || radiusKm > 50)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Ban kinh tim kiem phai lon hon 0 va toi da 50 km");
        }
        if (hasLat) {
            return new LocationContext(true, lat, lng, radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm);
        }
        if (preference != null
                && preference.getDefaultLat() != null
                && preference.getDefaultLng() != null
                && preference.getDefaultRadiusKm() != null) {
            return new LocationContext(true, preference.getDefaultLat(), preference.getDefaultLng(),
                    preference.getDefaultRadiusKm());
        }
        return new LocationContext(false, null, null, radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm);
    }

    private UserDiscoveryPreference loadPreference(UUID userId) {
        if (userId == null) return null;
        return preferenceRepository.findByUserId(userId).orElse(null);
    }

    private double numberParam(RuleDefinition definition, String key, double fallback) {
        Object raw = definition.params().get(key);
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

    private int integerParam(RuleDefinition definition, String key, int fallback) {
        Object raw = definition.params().get(key);
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

    private double doubleOrZero(Double value) {
        return value == null ? 0 : value;
    }

    private int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record DiscoveryCandidate(BagDiscoveryProjection row, PublicBagSummaryResponse summary, double baseScore) {
    }

    private record ManualCandidate(Integer pinnedOrder, DiscoveryCandidate value) {
    }

    private record LocationContext(boolean hasLocation, Double lat, Double lng, double radiusKm) {
    }

    private record RuleDefinition(DiscoveryRule rule, DiscoverySort sort, Map<String, Object> params) {

        @SuppressWarnings("unchecked")
        static RuleDefinition from(Map<String, Object> value) {
            if (value == null || value.isEmpty()) {
                return new RuleDefinition(DiscoveryRule.MANUAL, DiscoverySort.RANKING_DESC, Map.of());
            }
            DiscoveryRule rule = DiscoveryRule.valueOf(String.valueOf(value.get("rule")));
            DiscoverySort sort = DiscoverySort.valueOf(String.valueOf(value.get("sort")));
            Map<String, Object> params = value.get("params") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            return new RuleDefinition(rule, sort, params);
        }
    }
}
