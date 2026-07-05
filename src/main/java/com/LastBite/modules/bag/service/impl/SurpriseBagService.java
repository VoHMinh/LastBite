package com.LastBite.modules.bag.service.impl;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.bag.dto.request.AdjustTodayStockRequest;
import com.LastBite.modules.bag.dto.request.CreateSurpriseBagRequest;
import com.LastBite.modules.bag.dto.request.SetDailyStockRequest;
import com.LastBite.modules.bag.dto.request.UpdateSurpriseBagRequest;
import com.LastBite.modules.bag.dto.request.WeeklyStockPlanItemRequest;
import com.LastBite.modules.bag.dto.response.BagPriceTierSummaryResponse;
import com.LastBite.modules.bag.dto.response.DailyStockResponse;
import com.LastBite.modules.bag.dto.response.StockAuditLogResponse;
import com.LastBite.modules.bag.dto.response.StockCalendarResponse;
import com.LastBite.modules.bag.dto.response.SurpriseBagResponse;
import com.LastBite.modules.bag.dto.response.WeeklyStockPlanItemResponse;
import com.LastBite.modules.bag.entity.BagPriceTier;
import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.entity.StockAuditLog;
import com.LastBite.modules.bag.entity.SurpriseBag;
import com.LastBite.modules.bag.enums.BagStatus;
import com.LastBite.modules.bag.enums.DailyStockSource;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import com.LastBite.modules.bag.enums.DietType;
import com.LastBite.modules.bag.enums.StockAuditAction;
import com.LastBite.modules.bag.enums.StockAuditActorType;
import com.LastBite.modules.bag.repository.BagDailyStockRepository;
import com.LastBite.modules.bag.repository.BagPriceTierRepository;
import com.LastBite.modules.bag.repository.StockAuditLogRepository;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.bag.service.SurpriseBagServicePort;
import com.LastBite.modules.notification.service.NotificationServicePort;
import com.LastBite.modules.merchant.service.StoreAccessService;
import com.LastBite.modules.auth.enums.UserRole;
import com.LastBite.modules.media.service.MediaUrlService;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.enums.StoreStatus;
import com.LastBite.modules.store.enums.VerificationStatus;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurpriseBagService implements SurpriseBagServicePort {

    private static final int MAX_STOCK_PER_DAY = 50;
    private static final int MIN_PICKUP_MINUTES = 30;
    private static final int MAX_PICKUP_MINUTES = 240;
    private static final int STOCK_FORECAST_DAYS = 7;
    private static final String DEFAULT_PACKAGING_NOTE = "We recommend bringing your own bag.";

    private final SurpriseBagRepository bagRepository;
    private final BagDailyStockRepository stockRepository;
    private final BagPriceTierRepository priceTierRepository;
    private final StockAuditLogRepository auditLogRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final BagPricingService pricingService;
    private final NotificationServicePort notificationService;
    private final StoreAccessService storeAccessService;
    private final MediaUrlService mediaUrlService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SurpriseBagResponse getById(UUID actorId, UUID bagId) {
        SurpriseBag bag = getOwnedBag(actorId, bagId);
        return toBagResponse(bag, findTodayStock(bag.getId()));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public SurpriseBagResponse create(UUID ownerId, CreateSurpriseBagRequest request) {
        Store store = getReadyStore(ownerId);
        return createForStore(store, request);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public SurpriseBagResponse create(UUID actorId, UUID storeId, CreateSurpriseBagRequest request) {
        Store store = storeAccessService.require(actorId, storeId, java.util.Set.of(UserRole.MANAGER));
        if (store.getStatus() != StoreStatus.ACTIVE || store.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Cửa hàng phải được duyệt và đang hoạt động");
        }
        return createForStore(store, request);
    }

    private SurpriseBagResponse createForStore(Store store, CreateSurpriseBagRequest request) {
        validatePickupWindow(request.getPickupStartTime(), request.getPickupEndTime());
        BagPriceTier tier = getPriceTier(request.getCategory(), request.getBagSize());
        Integer[] availableDays = toDayArray(request.getAvailableDays().stream().toList());
        Integer[] weeklyStockPlan = toWeeklyStockPlan(request.getWeeklyStockPlan(), Set.of(availableDays));

        SurpriseBag bag = SurpriseBag.builder()
                .store(store)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .bagType(request.getBagType() == null ? com.LastBite.modules.bag.enums.BagType.STANDARD : request.getBagType())
                .dietType(request.getDietType() == null ? DietType.MEAT : request.getDietType())
                .category(tier.getCategory())
                .bagSize(tier.getBagSize())
                .photos(toStringArray(request.getPhotos()))
                .minimumValue(tier.getMinimumValue())
                .baseSalePrice(tier.getBaseSalePrice())
                .dynamicMinPrice(tier.getDynamicMinPrice())
                .dynamicMaxPrice(tier.getDynamicMaxPrice())
                .dynamicPricingEnabled(request.getDynamicPricingEnabled() == null || request.getDynamicPricingEnabled())
                .platformFee(tier.getPlatformFee())
                .maxPerOrder(request.getMaxPerOrder() == null ? 1 : request.getMaxPerOrder())
                .containerProvided(request.getContainerProvided() == null || request.getContainerProvided())
                .carrierBagProvided(request.getCarrierBagProvided() == null || request.getCarrierBagProvided())
                .packagingNote(request.getPackagingNote() == null
                        ? DEFAULT_PACKAGING_NOTE
                        : trimToNull(request.getPackagingNote()))
                .pickupStartTime(request.getPickupStartTime())
                .pickupEndTime(request.getPickupEndTime())
                .availableDays(availableDays)
                .weeklyStockPlan(weeklyStockPlan)
                .status(BagStatus.ACTIVE)
                .build();

        bag = bagRepository.save(bag);
        log.info("Đã tạo túi bất ngờ {} cho cửa hàng {}", bag.getId(), store.getSlug());
        return toBagResponse(bag, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<SurpriseBagResponse> list(UUID ownerId, Pageable pageable) {
        var page = bagRepository.findByStoreBusinessProfileOwnerIdAndStatusNot(
                        ownerId, BagStatus.ARCHIVED, pageable)
                .map(bag -> toBagResponse(bag, findTodayStock(bag.getId())));
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<SurpriseBagResponse> list(UUID actorId, UUID storeId, Pageable pageable) {
        storeAccessService.require(actorId, storeId, java.util.Set.of(UserRole.MANAGER, UserRole.STAFF));
        var page = bagRepository.findByStoreIdAndStatusNot(storeId, BagStatus.ARCHIVED, pageable)
                .map(bag -> toBagResponse(bag, findTodayStock(bag.getId())));
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public SurpriseBagResponse update(UUID ownerId, UUID bagId, UpdateSurpriseBagRequest request) {
        SurpriseBag bag = getOwnedBag(ownerId, bagId);
        ensureNotArchived(bag);

        var pickupStart = request.getPickupStartTime() != null ? request.getPickupStartTime() : bag.getPickupStartTime();
        var pickupEnd = request.getPickupEndTime() != null ? request.getPickupEndTime() : bag.getPickupEndTime();
        validatePickupWindow(pickupStart, pickupEnd);
        Integer[] availableDays = request.getAvailableDays() != null
                ? toDayArray(request.getAvailableDays().stream().toList())
                : normalizeDayArray(bag.getAvailableDays());
        Integer[] weeklyStockPlan = request.getWeeklyStockPlan() != null
                ? toWeeklyStockPlan(request.getWeeklyStockPlan(), Set.of(availableDays))
                : normalizeWeeklyStockPlan(bag.getWeeklyStockPlan(), Set.of(availableDays));

        if (request.getName() != null && !request.getName().isBlank()) bag.setName(request.getName().trim());
        if (request.getDescription() != null) bag.setDescription(trimToNull(request.getDescription()));
        if (request.getBagType() != null) bag.setBagType(request.getBagType());
        if (request.getDietType() != null) bag.setDietType(request.getDietType());
        if (request.getCategory() != null || request.getBagSize() != null) {
            var category = request.getCategory() != null ? request.getCategory() : bag.getCategory();
            var bagSize = request.getBagSize() != null ? request.getBagSize() : bag.getBagSize();
            applyPriceTierSnapshot(bag, getPriceTier(category, bagSize));
        }
        if (request.getPhotos() != null) bag.setPhotos(toStringArray(request.getPhotos()));
        if (request.getDynamicPricingEnabled() != null) bag.setDynamicPricingEnabled(request.getDynamicPricingEnabled());
        if (request.getMaxPerOrder() != null) bag.setMaxPerOrder(request.getMaxPerOrder());
        if (request.getContainerProvided() != null) bag.setContainerProvided(request.getContainerProvided());
        if (request.getCarrierBagProvided() != null) bag.setCarrierBagProvided(request.getCarrierBagProvided());
        if (request.getPackagingNote() != null) bag.setPackagingNote(trimToNull(request.getPackagingNote()));
        if (request.getPickupStartTime() != null) bag.setPickupStartTime(request.getPickupStartTime());
        if (request.getPickupEndTime() != null) bag.setPickupEndTime(request.getPickupEndTime());
        if (request.getAvailableDays() != null) bag.setAvailableDays(availableDays);
        if (request.getWeeklyStockPlan() != null || request.getAvailableDays() != null) {
            bag.setWeeklyStockPlan(weeklyStockPlan);
        }

        bag = bagRepository.save(bag);
        log.info("Đã cập nhật túi bất ngờ {}", bag.getId());
        return toBagResponse(bag, findTodayStock(bag.getId()));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public void softDelete(UUID ownerId, UUID bagId) {
        SurpriseBag bag = getOwnedBag(ownerId, bagId);
        bag.setStatus(BagStatus.ARCHIVED);
        bagRepository.save(bag);
        log.info("Đã lưu trữ túi bất ngờ {}", bagId);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public SurpriseBagResponse pause(UUID ownerId, UUID bagId) {
        SurpriseBag bag = getOwnedBag(ownerId, bagId);
        ensureNotArchived(bag);
        bag.setStatus(BagStatus.PAUSED);
        SurpriseBag saved = bagRepository.save(bag);
        notificationService.notifyMerchantBagPaused(saved);
        return toBagResponse(saved, findTodayStock(bagId));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public SurpriseBagResponse resume(UUID ownerId, UUID bagId) {
        SurpriseBag bag = getOwnedBag(ownerId, bagId);
        ensureNotArchived(bag);
        bag.setStatus(BagStatus.ACTIVE);
        SurpriseBag saved = bagRepository.save(bag);
        notificationService.notifyMerchantBagResumed(saved);
        return toBagResponse(saved, findTodayStock(bagId));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public DailyStockResponse setStock(UUID ownerId, UUID bagId, LocalDate date, SetDailyStockRequest request) {
        SurpriseBag bag = getOwnedBag(ownerId, bagId);
        ensureNotArchived(bag);
        validateStockDateAllowed(bag, date);
        validateStockQuantity(request.getQuantity(), 0, 0);

        User actor = getUser(ownerId);
        BagDailyStock stock = stockRepository.findByBagIdAndDateForUpdate(bagId, date)
                .orElseGet(() -> BagDailyStock.builder()
                        .bag(bag)
                        .store(bag.getStore())
                        .date(date)
                        .source(DailyStockSource.MANUAL)
                        .status(DailyStockStatus.ACTIVE)
                        .build());

        int availableBefore = stock.available();
        int before = stock.getQuantity();
        if (request.getQuantity() < stock.getReserved() + stock.getSold()) {
            throw new ApiException(ErrorCode.INVALID_INPUT,
                    "Số lượng mới không được nhỏ hơn số đã giữ và đã bán");
        }

        stock.setQuantity(request.getQuantity());
        stock.setSource(DailyStockSource.MANUAL);
        stock.setStatus(resolveStockStatus(stock));
        stock = stockRepository.save(stock);

        writeAudit(bag, stock, actor, StockAuditActorType.MERCHANT, StockAuditAction.STOCK_SET,
                stock.getQuantity() - before, before, stock.getQuantity(), request.getReason());
        notifyFavoriteStoreIfNewAvailability(date, stock, availableBefore);

        return toStockResponse(stock);
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public DailyStockResponse adjustTodayStock(UUID ownerId, UUID bagId, AdjustTodayStockRequest request) {
        if (request.getDelta() == 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Số lượng điều chỉnh phải khác 0");
        }

        LocalDate today = LocalDate.now(clock);
        SurpriseBag bag = getOwnedBag(ownerId, bagId);
        ensureNotArchived(bag);
        validateStockDateAllowed(bag, today);

        User actor = getUser(ownerId);
        BagDailyStock stock = stockRepository.findByBagIdAndDateForUpdate(bagId, today)
                .orElseGet(() -> BagDailyStock.builder()
                        .bag(bag)
                        .store(bag.getStore())
                        .date(today)
                        .source(DailyStockSource.MANUAL)
                        .status(DailyStockStatus.ACTIVE)
                        .build());

        int availableBefore = stock.available();
        int before = stock.getQuantity();
        int target = before + request.getDelta();
        if (target < stock.getReserved() + stock.getSold()) {
            throw new ApiException(ErrorCode.INVALID_INPUT,
                    "Không thể giảm thấp hơn số túi đã giữ hoặc đã bán");
        }
        validateStockQuantity(target, stock.getReserved(), stock.getSold());

        stock.setQuantity(target);
        stock.setSource(DailyStockSource.MANUAL);
        stock.setStatus(resolveStockStatus(stock));
        stock = stockRepository.save(stock);

        writeAudit(bag, stock, actor, StockAuditActorType.MERCHANT,
                request.getDelta() > 0 ? StockAuditAction.STOCK_ADD : StockAuditAction.STOCK_REDUCE,
                request.getDelta(), before, target, request.getReason());
        notifyFavoriteStoreIfNewAvailability(today, stock, availableBefore);

        return toStockResponse(stock);
    }

    @Transactional(readOnly = true)
    public List<StockCalendarResponse> stockCalendar(UUID ownerId, UUID bagId, LocalDate from, LocalDate to) {
        SurpriseBag bag = getOwnedBag(ownerId, bagId);
        ensureNotArchived(bag);
        LocalDate start = from == null ? LocalDate.now(clock) : from;
        LocalDate end = to == null ? start.plusDays(14) : to;
        if (end.isBefore(start)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Ngay ket thuc phai sau ngay bat dau");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 31) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi duoc xem toi da 31 ngay moi lan");
        }

        Map<LocalDate, BagDailyStock> stocksByDate = new HashMap<>();
        for (BagDailyStock stock : stockRepository.findByBagIdAndDateBetweenOrderByDateAsc(bagId, start, end)) {
            stocksByDate.put(stock.getDate(), stock);
        }

        List<StockCalendarResponse> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            BagDailyStock stock = stocksByDate.get(date);
            result.add(stock == null ? virtualStockCalendarRow(bag, date) : toStockCalendarResponse(stock));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<StockAuditLogResponse> auditLogs(UUID ownerId, UUID bagId, Pageable pageable) {
        SurpriseBag bag = getOwnedBag(ownerId, bagId);
        var page = auditLogRepository.findByBagIdOrderByCreatedAtDesc(bag.getId(), pageable)
                .map(this::toAuditResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public BagPriceTierSummaryResponse getActivePriceTier(
            com.LastBite.modules.store.enums.StoreCategory category,
            com.LastBite.modules.bag.enums.BagSize bagSize) {
        return priceTierRepository.findByCategoryAndBagSizeAndActiveTrue(category, bagSize)
                .map(this::toTierSummary)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<BagPriceTierSummaryResponse> listActivePriceTiers(
            com.LastBite.modules.store.enums.StoreCategory category) {
        List<BagPriceTier> tiers = category == null
                ? priceTierRepository.findByActiveTrueOrderByCategoryAscBagSizeAsc()
                : priceTierRepository.findByCategoryAndActiveTrueOrderByBagSizeAsc(category);
        return tiers.stream().map(this::toTierSummary).toList();
    }

    private BagPriceTierSummaryResponse toTierSummary(BagPriceTier tier) {
        BigDecimal finalPrice = tier.getBaseSalePrice().add(tier.getPlatformFee());
        return BagPriceTierSummaryResponse.builder()
                .id(tier.getId())
                .category(tier.getCategory())
                .bagSize(tier.getBagSize())
                .minimumValue(tier.getMinimumValue())
                .baseSalePrice(tier.getBaseSalePrice())
                .dynamicMinPrice(tier.getDynamicMinPrice())
                .dynamicMaxPrice(tier.getDynamicMaxPrice())
                .platformFee(tier.getPlatformFee())
                .finalPrice(finalPrice)
                .build();
    }

    @Transactional(readOnly = true)
    public List<com.LastBite.modules.store.enums.StoreCategory> listAvailableCategories() {
        return priceTierRepository.findDistinctActiveCategoriesOrderByCategory();
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public int createTodayStocks() {
        return createStocksForDate(LocalDate.now(clock));
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public int createUpcomingStocks() {
        int created = 0;
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        for (int offset = 0; offset < STOCK_FORECAST_DAYS; offset++) {
            created += createStocksForDate(tomorrow.plusDays(offset));
        }
        return created;
    }

    private int createStocksForDate(LocalDate date) {
        List<SurpriseBag> bags = bagRepository.findActiveBagsMissingStockForDate(date);
        int created = 0;
        for (SurpriseBag bag : bags) {
            if (!isAvailableOnDate(bag, date)) {
                continue;
            }
            int quantity = weeklyQuantityForDate(bag, date);
            if (quantity <= 0) {
                continue;
            }
            BagDailyStock stock = BagDailyStock.builder()
                    .bag(bag)
                    .store(bag.getStore())
                    .date(date)
                    .quantity(quantity)
                    .reserved(0)
                    .sold(0)
                    .source(DailyStockSource.WEEKLY_DEFAULT)
                    .status(DailyStockStatus.ACTIVE)
                    .build();
            stockRepository.save(stock);
            created++;
        }
        return created;
    }

    @Transactional
    @CacheEvict(value = {"bag-discovery", "home-discovery", "bag-detail", "store-bags"}, allEntries = true)
    public int expireUnsoldStocks() {
        LocalDate today = LocalDate.now(clock);
        var now = java.time.LocalTime.now(clock);
        List<BagDailyStock> stocks = stockRepository.findStocksToExpire(DailyStockStatus.ACTIVE, today, now);
        int expired = 0;
        for (BagDailyStock stock : stocks) {
            if (stock.available() <= 0) {
                continue;
            }
            int before = stock.getQuantity();
            int after = stock.getReserved() + stock.getSold();
            stock.setQuantity(after);
            stock.setStatus(DailyStockStatus.EXPIRED);
            stock = stockRepository.save(stock);
            writeAudit(stock.getBag(), stock, null, StockAuditActorType.SYSTEM, StockAuditAction.EXPIRE_UNSOLD,
                    after - before, before, after, "Hệ thống tự động hết hạn túi chưa bán sau giờ pickup");
            expired++;
        }
        return expired;
    }

    private Store getReadyStore(UUID ownerId) {
        Store store = storeRepository.findFirstByBusinessProfileOwnerIdOrderByCreatedAtAsc(ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND, "Bạn chưa có cửa hàng"));
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Cửa hàng chưa ở trạng thái hoạt động");
        }
        if (store.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Cửa hàng cần được xác minh trước khi bán túi");
        }
        return store;
    }

    private SurpriseBag getOwnedBag(UUID ownerId, UUID bagId) {
        return bagRepository.findByIdAndStoreBusinessProfileOwnerId(bagId, ownerId)
                .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    private void validatePickupWindow(java.time.LocalTime pickupStart, java.time.LocalTime pickupEnd) {
        long minutes = Duration.between(pickupStart, pickupEnd).toMinutes();
        if (minutes < MIN_PICKUP_MINUTES) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Khung giờ pickup tối thiểu 30 phút");
        }
        if (minutes > MAX_PICKUP_MINUTES) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Khung giờ pickup tối đa 4 tiếng");
        }
    }

    private void validateStockDateAllowed(SurpriseBag bag, LocalDate date) {
        if (!isAvailableOnDate(bag, date)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Túi không mở bán vào ngày này");
        }
    }

    private boolean isAvailableOnDate(SurpriseBag bag, LocalDate date) {
        int day = weekdayIndex(date);
        return Arrays.asList(bag.getAvailableDays()).contains(day);
    }

    private int weeklyQuantityForDate(SurpriseBag bag, LocalDate date) {
        Integer[] plan = normalizeWeeklyStockPlan(
                bag.getWeeklyStockPlan(),
                Set.of(normalizeDayArray(bag.getAvailableDays())));
        return plan[weekdayIndex(date)];
    }

    private int weekdayIndex(LocalDate date) {
        return date.getDayOfWeek().getValue() % 7;
    }

    private Integer[] toWeeklyStockPlan(List<WeeklyStockPlanItemRequest> values, Set<Integer> availableDays) {
        Integer[] plan = zeroWeeklyStockPlan();
        if (values == null) {
            return plan;
        }
        boolean[] seen = new boolean[7];
        for (WeeklyStockPlanItemRequest item : values) {
            if (item == null || item.getDayOfWeek() == null || item.getQuantity() == null) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Lich so luong mac dinh khong hop le");
            }
            int day = item.getDayOfWeek();
            int quantity = item.getQuantity();
            if (day < 0 || day > 6 || quantity < 0 || quantity > MAX_STOCK_PER_DAY) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Lich so luong mac dinh khong hop le");
            }
            if (seen[day]) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Ngay trong weeklyStockPlan bi trung");
            }
            if (quantity > 0 && !availableDays.contains(day)) {
                throw new ApiException(ErrorCode.INVALID_INPUT,
                        "Chi duoc set so luong mac dinh cho ngay bag mo ban");
            }
            seen[day] = true;
            plan[day] = quantity;
        }
        return plan;
    }

    private Integer[] normalizeWeeklyStockPlan(Integer[] values, Set<Integer> availableDays) {
        Integer[] plan = zeroWeeklyStockPlan();
        if (values == null) {
            return plan;
        }
        if (values.length != 7) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "weeklyStockPlan phai co du 7 ngay");
        }
        for (int day = 0; day < values.length; day++) {
            int quantity = values[day] == null ? 0 : values[day];
            if (quantity < 0 || quantity > MAX_STOCK_PER_DAY) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Lich so luong mac dinh khong hop le");
            }
            if (quantity > 0 && !availableDays.contains(day)) {
                throw new ApiException(ErrorCode.INVALID_INPUT,
                        "Chi duoc set so luong mac dinh cho ngay bag mo ban");
            }
            plan[day] = quantity;
        }
        return plan;
    }

    private Integer[] zeroWeeklyStockPlan() {
        return new Integer[]{0, 0, 0, 0, 0, 0, 0};
    }

    private void validateStockQuantity(int quantity, int reserved, int sold) {
        if (quantity > MAX_STOCK_PER_DAY) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Nghi ngờ gian lận: mỗi túi chỉ được set tối đa 50 phần/ngày");
        }
        if (quantity < 0 || reserved < 0 || sold < 0 || reserved + sold > quantity) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Số lượng tồn kho không hợp lệ");
        }
    }

    private DailyStockStatus resolveStockStatus(BagDailyStock stock) {
        if (stock.getStatus() == DailyStockStatus.CANCELLED || stock.getStatus() == DailyStockStatus.EXPIRED) {
            return stock.getStatus();
        }
        return stock.available() <= 0 && stock.getQuantity() > 0
                ? DailyStockStatus.SOLD_OUT
                : DailyStockStatus.ACTIVE;
    }

    private void ensureNotArchived(SurpriseBag bag) {
        if (bag.getStatus() == BagStatus.ARCHIVED) {
            throw new ApiException(ErrorCode.BAG_NOT_FOUND);
        }
    }

    private BagDailyStock findTodayStock(UUID bagId) {
        return stockRepository.findByBagIdAndDate(bagId, LocalDate.now(clock)).orElse(null);
    }

    private BagPriceTier getPriceTier(com.LastBite.modules.store.enums.StoreCategory category,
                                      com.LastBite.modules.bag.enums.BagSize bagSize) {
        return priceTierRepository.findByCategoryAndBagSizeAndActiveTrue(category, bagSize)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INPUT,
                        "Platform chưa cấu hình gói giá cho danh mục và kích cỡ túi này"));
    }

    private void applyPriceTierSnapshot(SurpriseBag bag, BagPriceTier tier) {
        bag.setCategory(tier.getCategory());
        bag.setBagSize(tier.getBagSize());
        bag.setMinimumValue(tier.getMinimumValue());
        bag.setBaseSalePrice(tier.getBaseSalePrice());
        bag.setDynamicMinPrice(tier.getDynamicMinPrice());
        bag.setDynamicMaxPrice(tier.getDynamicMaxPrice());
        bag.setPlatformFee(tier.getPlatformFee());
    }

    private void writeAudit(SurpriseBag bag, BagDailyStock stock, User actor, StockAuditActorType actorType,
                            StockAuditAction action, int delta, int before, int after, String reason) {
        auditLogRepository.save(StockAuditLog.builder()
                .bag(bag)
                .dailyStock(stock)
                .actor(actor)
                .actorType(actorType)
                .action(action)
                .delta(delta)
                .quantityBefore(before)
                .quantityAfter(after)
                .reason(trimToNull(reason))
                .build());
    }

    private void notifyFavoriteStoreIfNewAvailability(LocalDate date, BagDailyStock stock, int availableBefore) {
        if (!date.equals(LocalDate.now(clock))) {
            return;
        }
        if (availableBefore <= 0 && stock.available() > 0) {
            notificationService.notifyFavoriteStoreStockAvailable(stock);
        }
    }

    private SurpriseBagResponse toBagResponse(SurpriseBag bag, BagDailyStock todayStock) {
        var price = pricingService.currentPrice(bag, todayStock == null ? LocalDate.now(clock) : todayStock.getDate());
        return SurpriseBagResponse.builder()
                .id(bag.getId())
                .storeId(bag.getStore().getId())
                .storeName(bag.getStore().getName())
                .name(bag.getName())
                .description(bag.getDescription())
                .bagType(bag.getBagType())
                .dietType(bag.getDietType())
                .category(bag.getCategory())
                .bagSize(bag.getBagSize())
                .photos(bag.getPhotos() == null ? List.of() : mediaUrlService.resolveUrls(Arrays.asList(bag.getPhotos())))
                .minimumValue(bag.getMinimumValue())
                .baseSalePrice(bag.getBaseSalePrice())
                .currentSalePrice(price.currentSalePrice())
                .savingsAmount(price.savingsAmount())
                .currentDiscountPercent(price.currentDiscountPercent())
                .dynamicMinPrice(bag.getDynamicMinPrice())
                .dynamicMaxPrice(bag.getDynamicMaxPrice())
                .dynamicPricingEnabled(bag.isDynamicPricingEnabled())
                .platformFee(bag.getPlatformFee())
                .maxPerOrder(bag.getMaxPerOrder())
                .containerProvided(bag.isContainerProvided())
                .carrierBagProvided(bag.isCarrierBagProvided())
                .packagingNote(bag.getPackagingNote())
                .pickupStartTime(bag.getPickupStartTime())
                .pickupEndTime(bag.getPickupEndTime())
                .availableDays(Arrays.stream(bag.getAvailableDays()).sorted().toList())
                .weeklyStockPlan(toWeeklyStockPlanResponse(bag.getWeeklyStockPlan()))
                .status(bag.getStatus())
                .version(bag.getVersion())
                .todayStock(todayStock == null ? null : toStockResponse(todayStock))
                .createdAt(bag.getCreatedAt())
                .updatedAt(bag.getUpdatedAt())
                .build();
    }

    private DailyStockResponse toStockResponse(BagDailyStock stock) {
        return DailyStockResponse.builder()
                .id(stock.getId())
                .bagId(stock.getBag().getId())
                .date(stock.getDate())
                .quantity(stock.getQuantity())
                .reserved(stock.getReserved())
                .sold(stock.getSold())
                .available(stock.available())
                .status(stock.getStatus())
                .version(stock.getVersion())
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt())
                .build();
    }

    private StockAuditLogResponse toAuditResponse(StockAuditLog log) {
        User actor = log.getActor();
        BagDailyStock stock = log.getDailyStock();
        return StockAuditLogResponse.builder()
                .id(log.getId())
                .bagId(log.getBag().getId())
                .dailyStockId(stock == null ? null : stock.getId())
                .stockDate(stock == null ? null : stock.getDate())
                .actorId(actor == null ? null : actor.getId())
                .actorEmail(actor == null ? null : actor.getEmail())
                .actorType(log.getActorType())
                .action(log.getAction())
                .delta(log.getDelta())
                .quantityBefore(log.getQuantityBefore())
                .quantityAfter(log.getQuantityAfter())
                .reason(log.getReason())
                .orderId(log.getOrderId())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private StockCalendarResponse virtualStockCalendarRow(SurpriseBag bag, LocalDate date) {
        int quantity = isAvailableOnDate(bag, date) ? weeklyQuantityForDate(bag, date) : 0;
        return StockCalendarResponse.builder()
                .dailyStockId(null)
                .date(date)
                .quantity(quantity)
                .reserved(0)
                .sold(0)
                .available(quantity)
                .status(quantity > 0 ? DailyStockStatus.ACTIVE : null)
                .source(DailyStockSource.WEEKLY_DEFAULT)
                .build();
    }

    private StockCalendarResponse toStockCalendarResponse(BagDailyStock stock) {
        DailyStockSource source = stock.getSource() == null ? DailyStockSource.MANUAL : stock.getSource();
        return StockCalendarResponse.builder()
                .dailyStockId(stock.getId())
                .date(stock.getDate())
                .quantity(stock.getQuantity())
                .reserved(stock.getReserved())
                .sold(stock.getSold())
                .available(stock.available())
                .status(stock.getStatus())
                .source(source)
                .build();
    }

    private List<WeeklyStockPlanItemResponse> toWeeklyStockPlanResponse(Integer[] values) {
        Integer[] plan = values == null || values.length != 7 ? zeroWeeklyStockPlan() : values;
        List<WeeklyStockPlanItemResponse> response = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            response.add(WeeklyStockPlanItemResponse.builder()
                    .dayOfWeek(day)
                    .quantity(plan[day] == null ? 0 : plan[day])
                    .build());
        }
        return response;
    }

    private String[] toStringArray(List<String> values) {
        if (values == null) return new String[0];
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toArray(String[]::new);
    }

    private Integer[] toDayArray(List<Integer> values) {
        if (values.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Cần chọn ít nhất 1 ngày bán");
        }
        return values.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toArray(Integer[]::new);
    }

    private Integer[] normalizeDayArray(Integer[] values) {
        if (values == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Can chon it nhat 1 ngay ban");
        }
        return toDayArray(Arrays.asList(values));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
