package com.LastBite.modules.analytics.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.util.HashUtil;
import com.LastBite.modules.analytics.dto.request.TrackEngagementEventRequest;
import com.LastBite.modules.analytics.dto.response.StoreEngagementAnalyticsResponse;
import com.LastBite.modules.analytics.dto.response.TrackEngagementEventResponse;
import com.LastBite.modules.analytics.entity.StoreEngagementEvent;
import com.LastBite.modules.analytics.enums.EngagementEventType;
import com.LastBite.modules.analytics.repository.OrderAnalyticsProjection;
import com.LastBite.modules.analytics.repository.StoreEngagementEventRepository;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.merchant.service.StoreAccessService;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreEngagementAnalyticsService {

    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final Set<UserRole> ANALYTICS_ROLES = Set.of(UserRole.MANAGER, UserRole.STAFF);

    private final StoreEngagementEventRepository eventRepository;
    private final StoreRepository storeRepository;
    private final SurpriseBagRepository bagRepository;
    private final UserRepository userRepository;
    private final StoreAccessService storeAccessService;
    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TrackEngagementEventResponse track(UUID userId, TrackEngagementEventRequest request,
                                              HttpServletRequest httpRequest) {
        if (request.getEventType() == null) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "eventType la bat buoc");
        }
        UUID storeId = resolveStoreId(request);
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND));
        SurpriseBag bag = null;
        if (request.getBagId() != null) {
            bag = bagRepository.findByIdAndStoreId(request.getBagId(), storeId)
                    .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
        }
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        StoreEngagementEvent event = saveEvent(store, bag, user, request.getEventType(), request.getSource(),
                request.getSessionId(), httpRequest);
        return TrackEngagementEventResponse.builder()
                .eventId(event.getId())
                .occurredAt(event.getOccurredAt())
                .build();
    }

    public void recordStoreViewSafely(UUID storeId, UUID userId, String source, HttpServletRequest request) {
        recordSafely(storeId, null, userId, EngagementEventType.STORE_VIEW, source, request);
    }

    public void recordBagViewSafely(UUID storeId, UUID bagId, UUID userId, String source, HttpServletRequest request) {
        recordSafely(storeId, bagId, userId, EngagementEventType.BAG_VIEW, source, request);
    }

    @Transactional(readOnly = true)
    public StoreEngagementAnalyticsResponse merchantAnalytics(UUID userId, UUID storeId, Instant from, Instant to,
                                                              String timezone) {
        storeAccessService.require(userId, storeId, ANALYTICS_ROLES);
        ZoneId zone = zone(timezone);
        Range range = normalizeRange(from, to, zone);
        String zoneId = zone.getId();

        var summary = eventRepository.summarize(storeId, range.from(), range.to());
        var orderSummary = orderRepository.summarizeStoreOrders(storeId, range.from(), range.to());

        long storeViews = summary == null ? 0 : summary.getStoreViews();
        long storeCardClicks = summary == null ? 0 : summary.getStoreCardClicks();
        long bagViews = summary == null ? 0 : summary.getBagViews();
        long bagCardClicks = summary == null ? 0 : summary.getBagCardClicks();
        long totalEngagements = storeViews + storeCardClicks + bagViews + bagCardClicks;
        long paidOrders = orderSummary == null ? 0 : orderSummary.getPaidOrders();

        return StoreEngagementAnalyticsResponse.builder()
                .storeId(storeId)
                .from(range.from())
                .to(range.to())
                .timezone(zoneId)
                .overview(StoreEngagementAnalyticsResponse.Overview.builder()
                        .storeViews(storeViews)
                        .storeCardClicks(storeCardClicks)
                        .bagViews(bagViews)
                        .bagCardClicks(bagCardClicks)
                        .totalEngagements(totalEngagements)
                        .knownUsers(summary == null ? 0 : summary.getKnownUsers())
                        .anonymousSessions(summary == null ? 0 : summary.getAnonymousSessions())
                        .totalOrders(orderSummary == null ? 0 : orderSummary.getTotalOrders())
                        .paidOrders(paidOrders)
                        .bagsSold(orderSummary == null ? 0 : orderSummary.getBagsSold())
                        .grossRevenue(orderSummary == null || orderSummary.getGrossRevenue() == null
                                ? BigDecimal.ZERO : orderSummary.getGrossRevenue())
                        .paidOrderConversionRate(conversionRate(paidOrders, totalEngagements))
                        .build())
                .hourly(eventRepository.hourly(storeId, range.from(), range.to(), zoneId).stream()
                        .map(item -> StoreEngagementAnalyticsResponse.HourlyBucket.builder()
                                .hour(item.getHour())
                                .storeViews(item.getStoreViews())
                                .bagViews(item.getBagViews())
                                .cardClicks(item.getCardClicks())
                                .totalEvents(item.getTotalEvents())
                                .build())
                        .toList())
                .daily(eventRepository.daily(storeId, range.from(), range.to(), zoneId).stream()
                        .map(item -> StoreEngagementAnalyticsResponse.DailyBucket.builder()
                                .date(item.getDate())
                                .storeViews(item.getStoreViews())
                                .bagViews(item.getBagViews())
                                .cardClicks(item.getCardClicks())
                                .totalEvents(item.getTotalEvents())
                                .build())
                        .toList())
                .sources(eventRepository.sourceBreakdown(storeId, range.from(), range.to()).stream()
                        .map(item -> StoreEngagementAnalyticsResponse.SourceBucket.builder()
                                .source(item.getSource())
                                .totalEvents(item.getTotalEvents())
                                .build())
                        .toList())
                .topBags(eventRepository.topBags(storeId, range.from(), range.to(), PageRequest.of(0, 10)).stream()
                        .map(item -> StoreEngagementAnalyticsResponse.TopBagBucket.builder()
                                .bagId(item.getBagId())
                                .bagName(item.getBagName())
                                .views(item.getViews())
                                .cardClicks(item.getCardClicks())
                                .totalEvents(item.getTotalEvents())
                                .build())
                        .toList())
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void record(UUID storeId, UUID bagId, UUID userId, EngagementEventType eventType, String source,
                          HttpServletRequest request) {
        Store store = storeRepository.getReferenceById(storeId);
        SurpriseBag bag = bagId == null ? null : bagRepository.getReferenceById(bagId);
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        saveEvent(store, bag, user, eventType, source, null, request);
    }

    private void recordSafely(UUID storeId, UUID bagId, UUID userId, EngagementEventType eventType, String source,
                              HttpServletRequest request) {
        try {
            record(storeId, bagId, userId, eventType, source, request);
        } catch (RuntimeException ex) {
            log.warn("Could not record store engagement event storeId={} bagId={} type={}",
                    storeId, bagId, eventType, ex);
        }
    }

    private StoreEngagementEvent saveEvent(Store store, SurpriseBag bag, User user, EngagementEventType eventType,
                                           String source, String sessionId, HttpServletRequest request) {
        StoreEngagementEvent event = StoreEngagementEvent.builder()
                .store(store)
                .bag(bag)
                .user(user)
                .eventType(eventType)
                .source(limit(blankToNull(source), 80))
                .sessionId(limit(firstNonBlank(sessionId, header(request, "X-LastBite-Session-Id")), 120))
                .referrer(limit(header(request, "Referer"), 500))
                .userAgent(limit(header(request, "User-Agent"), 500))
                .ipHash(ipHash(request))
                .occurredAt(Instant.now(clock))
                .build();
        return eventRepository.save(event);
    }

    private UUID resolveStoreId(TrackEngagementEventRequest request) {
        if (request.getStoreId() != null) {
            return request.getStoreId();
        }
        if (request.getBagId() == null) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "storeId hoac bagId la bat buoc");
        }
        return bagRepository.findById(request.getBagId())
                .map(bag -> bag.getStore().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
    }

    private ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim());
        } catch (DateTimeException ex) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Timezone khong hop le");
        }
    }

    private Range normalizeRange(Instant from, Instant to, ZoneId zone) {
        Instant normalizedTo = to == null ? Instant.now(clock) : to;
        Instant normalizedFrom = from == null
                ? ZonedDateTime.ofInstant(normalizedTo, zone).minusDays(30).toInstant()
                : from;
        if (!normalizedFrom.isBefore(normalizedTo)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "from phai nho hon to");
        }
        if (Duration.between(normalizedFrom, normalizedTo).toDays() > 366) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Khoang thong ke toi da 366 ngay");
        }
        return new Range(normalizedFrom, normalizedTo);
    }

    private double conversionRate(long paidOrders, long totalEngagements) {
        if (totalEngagements == 0) {
            return 0;
        }
        return Math.round((paidOrders * 10000.0 / totalEngagements)) / 100.0;
    }

    private String ipHash(HttpServletRequest request) {
        String ip = firstNonBlank(header(request, "X-Forwarded-For"), request.getRemoteAddr());
        if (ip == null) {
            return null;
        }
        int comma = ip.indexOf(',');
        String firstIp = comma >= 0 ? ip.substring(0, comma).trim() : ip.trim();
        return HashUtil.sha256(firstIp);
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? null : blankToNull(request.getHeader(name));
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : blankToNull(second);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record Range(Instant from, Instant to) {
    }
}
