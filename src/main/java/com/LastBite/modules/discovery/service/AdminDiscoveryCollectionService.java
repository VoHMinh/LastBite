package com.LastBite.modules.discovery.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.common.response.PageResponse;
import com.LastBite.modules.bag.repository.SurpriseBagRepository;
import com.LastBite.modules.discovery.dto.request.DiscoveryCollectionItemRequest;
import com.LastBite.modules.discovery.dto.request.DiscoveryCollectionRequest;
import com.LastBite.modules.discovery.dto.request.DiscoveryRuleDefinitionRequest;
import com.LastBite.modules.discovery.dto.request.UpdateDiscoveryCollectionItemRequest;
import com.LastBite.modules.discovery.dto.request.UpdateDiscoveryCollectionRequest;
import com.LastBite.modules.discovery.dto.response.DiscoveryCollectionItemResponse;
import com.LastBite.modules.discovery.dto.response.DiscoveryCollectionResponse;
import com.LastBite.modules.discovery.entity.DiscoveryCollection;
import com.LastBite.modules.discovery.entity.DiscoveryCollectionItem;
import com.LastBite.modules.discovery.enums.DiscoveryCollectionType;
import com.LastBite.modules.discovery.enums.DiscoveryRule;
import com.LastBite.modules.discovery.enums.DiscoverySort;
import com.LastBite.modules.discovery.repository.DiscoveryCollectionItemRepository;
import com.LastBite.modules.discovery.repository.DiscoveryCollectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminDiscoveryCollectionService {

    private final DiscoveryCollectionRepository collectionRepository;
    private final DiscoveryCollectionItemRepository itemRepository;
    private final SurpriseBagRepository bagRepository;

    @Transactional(readOnly = true)
    public PageResponse<DiscoveryCollectionResponse> list(Pageable pageable) {
        var page = collectionRepository.findAllByOrderByDisplayOrderAscSlugAsc(pageable).map(this::toResponse);
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DiscoveryCollectionResponse get(UUID collectionId) {
        return toResponse(getCollection(collectionId));
    }

    @Transactional
    @CacheEvict(value = {"home-discovery", "bag-discovery"}, allEntries = true)
    public DiscoveryCollectionResponse create(DiscoveryCollectionRequest request) {
        String slug = normalizeRequired(request.getSlug(), "Slug is required");
        String title = normalizeRequired(request.getTitle(), "Title is required");
        if (collectionRepository.existsBySlug(slug)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Discovery collection slug already exists");
        }
        validateShape(request.getType(), request.getRuleDefinition(), request.getMaxItems(),
                request.getMinItemsToDisplay());
        DiscoveryCollection collection = DiscoveryCollection.builder()
                .slug(slug)
                .title(title)
                .type(request.getType())
                .ruleDefinition(ruleMap(request.getType(), request.getRuleDefinition()))
                .displayOrder(request.getDisplayOrder())
                .maxItems(request.getMaxItems())
                .minItemsToDisplay(request.getMinItemsToDisplay())
                .active(request.getActive() == null || request.getActive())
                .build();
        return toResponse(collectionRepository.save(collection));
    }

    @Transactional
    @CacheEvict(value = {"home-discovery", "bag-discovery"}, allEntries = true)
    public DiscoveryCollectionResponse update(UUID collectionId, UpdateDiscoveryCollectionRequest request) {
        DiscoveryCollection collection = getCollection(collectionId);
        String slug = request.getSlug() == null ? collection.getSlug()
                : normalizeRequired(request.getSlug(), "Slug is required");
        if (!slug.equals(collection.getSlug())) {
            collectionRepository.findBySlug(slug)
                    .filter(existing -> !existing.getId().equals(collection.getId()))
                    .ifPresent(existing -> {
                        throw new ApiException(ErrorCode.DUPLICATE_RESOURCE,
                                "Discovery collection slug already exists");
                    });
        }
        DiscoveryCollectionType type = request.getType() == null ? collection.getType() : request.getType();
        DiscoveryRuleDefinitionRequest ruleDefinition = request.getRuleDefinition();
        int maxItems = request.getMaxItems() == null ? collection.getMaxItems() : request.getMaxItems();
        int minItems = request.getMinItemsToDisplay() == null
                ? collection.getMinItemsToDisplay()
                : request.getMinItemsToDisplay();
        validateLimits(maxItems, minItems);
        if (ruleDefinition != null) {
            validateRule(type, ruleDefinition);
        } else if (request.getType() != null && type == DiscoveryCollectionType.RULE_BASED) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Rule definition is required");
        }

        collection.setSlug(slug);
        if (request.getTitle() != null) {
            collection.setTitle(normalizeRequired(request.getTitle(), "Title is required"));
        }
        collection.setType(type);
        if (ruleDefinition != null || request.getType() != null) {
            collection.setRuleDefinition(ruleMap(type, ruleDefinition));
        }
        if (request.getDisplayOrder() != null) {
            collection.setDisplayOrder(request.getDisplayOrder());
        }
        collection.setMaxItems(maxItems);
        collection.setMinItemsToDisplay(minItems);
        if (request.getActive() != null) {
            collection.setActive(request.getActive());
        }
        return toResponse(collectionRepository.save(collection));
    }

    @Transactional
    @CacheEvict(value = {"home-discovery", "bag-discovery"}, allEntries = true)
    public DiscoveryCollectionResponse activate(UUID collectionId) {
        DiscoveryCollection collection = getCollection(collectionId);
        collection.setActive(true);
        return toResponse(collectionRepository.save(collection));
    }

    @Transactional
    @CacheEvict(value = {"home-discovery", "bag-discovery"}, allEntries = true)
    public DiscoveryCollectionResponse deactivate(UUID collectionId) {
        DiscoveryCollection collection = getCollection(collectionId);
        collection.setActive(false);
        return toResponse(collectionRepository.save(collection));
    }

    @Transactional(readOnly = true)
    public List<DiscoveryCollectionItemResponse> listItems(UUID collectionId) {
        getCollection(collectionId);
        return itemRepository.findByCollectionIdOrderByPinnedOrderAscAddedAtAsc(collectionId).stream()
                .map(this::toItemResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(value = {"home-discovery", "bag-discovery"}, allEntries = true)
    public DiscoveryCollectionItemResponse addItem(UUID collectionId, DiscoveryCollectionItemRequest request) {
        DiscoveryCollection collection = getCollection(collectionId);
        if (itemRepository.existsByCollectionIdAndBagId(collectionId, request.getBagId())) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Bag already exists in this collection");
        }
        var bag = bagRepository.findById(request.getBagId())
                .orElseThrow(() -> new ApiException(ErrorCode.BAG_NOT_FOUND));
        DiscoveryCollectionItem item = itemRepository.save(DiscoveryCollectionItem.builder()
                .collection(collection)
                .bag(bag)
                .pinnedOrder(request.getPinnedOrder())
                .build());
        return toItemResponse(item);
    }

    @Transactional
    @CacheEvict(value = {"home-discovery", "bag-discovery"}, allEntries = true)
    public DiscoveryCollectionItemResponse updateItem(UUID collectionId, UUID bagId,
                                                      UpdateDiscoveryCollectionItemRequest request) {
        DiscoveryCollectionItem item = itemRepository.findByCollectionIdAndBagId(collectionId, bagId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Collection item not found"));
        item.setPinnedOrder(request.getPinnedOrder());
        return toItemResponse(itemRepository.save(item));
    }

    @Transactional
    @CacheEvict(value = {"home-discovery", "bag-discovery"}, allEntries = true)
    public void deleteItem(UUID collectionId, UUID bagId) {
        long deleted = itemRepository.deleteByCollectionIdAndBagId(collectionId, bagId);
        if (deleted == 0) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Collection item not found");
        }
    }

    private DiscoveryCollection getCollection(UUID collectionId) {
        return collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Discovery collection not found"));
    }

    private void validateShape(DiscoveryCollectionType type, DiscoveryRuleDefinitionRequest ruleDefinition,
                               int maxItems, int minItems) {
        if (type == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Collection type is required");
        }
        validateLimits(maxItems, minItems);
        if (type == DiscoveryCollectionType.CURATED_MANUAL) {
            return;
        }
        if (ruleDefinition == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Rule definition is required");
        }
        validateRule(type, ruleDefinition);
    }

    private void validateLimits(int maxItems, int minItems) {
        if (maxItems < 1 || maxItems > 50 || minItems < 0 || minItems > maxItems) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Invalid collection item limits");
        }
    }
    private void validateRule(DiscoveryCollectionType type, DiscoveryRuleDefinitionRequest ruleDefinition) {
        if (type == DiscoveryCollectionType.PERSONALIZED
                && ruleDefinition.getRule() != DiscoveryRule.RECOMMENDED_FOR_YOU) {
            throw new ApiException(ErrorCode.INVALID_INPUT,
                    "Personalized collections must use RECOMMENDED_FOR_YOU");
        }
        if (type == DiscoveryCollectionType.RULE_BASED
                && List.of(DiscoveryRule.RECOMMENDED_FOR_YOU, DiscoveryRule.MANUAL).contains(ruleDefinition.getRule())) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Invalid rule for rule-based collection");
        }
        validateParams(ruleDefinition);
    }

    private void validateParams(DiscoveryRuleDefinitionRequest ruleDefinition) {
        switch (ruleDefinition.getRule()) {
            case NEAR_YOU -> requirePositive(number(ruleDefinition, "maxDistanceKm", 5));
            case BIG_DISCOUNT -> requireRange(number(ruleDefinition, "minDiscountPercent", 35), 0, 100);
            case UNDER_PRICE -> requirePositive(number(ruleDefinition, "maxPrice", 30000));
            case NEW_STORES -> requirePositive(number(ruleDefinition, "days", 30));
            case TOP_RATED -> {
                requireRange(number(ruleDefinition, "minRating", 4), 0, 5);
                requireRange(number(ruleDefinition, "minReviews", 5), 0, 100000);
            }
            default -> {
            }
        }
    }

    private Map<String, Object> ruleMap(DiscoveryCollectionType type, DiscoveryRuleDefinitionRequest request) {
        DiscoveryRule rule = request == null
                ? type == DiscoveryCollectionType.CURATED_MANUAL ? DiscoveryRule.MANUAL : DiscoveryRule.RECOMMENDED_FOR_YOU
                : request.getRule();
        DiscoverySort sort = request == null
                ? type == DiscoveryCollectionType.CURATED_MANUAL ? DiscoverySort.PINNED_THEN_RANKING : DiscoverySort.PERSONALIZED_DESC
                : request.getSort();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("rule", rule.name());
        value.put("sort", sort.name());
        value.put("params", request == null || request.getParams() == null
                ? Map.of()
                : new LinkedHashMap<>(request.getParams()));
        return value;
    }

    private double number(DiscoveryRuleDefinitionRequest request, String key, double fallback) {
        Object raw = request.getParams() == null ? null : request.getParams().get(key);
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

    private void requirePositive(double value) {
        if (value <= 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Rule parameter must be positive");
        }
    }

    private void requireRange(double value, double min, double max) {
        if (value < min || value > max) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Rule parameter is out of range");
        }
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, message);
        }
        return value.trim();
    }

    private DiscoveryCollectionResponse toResponse(DiscoveryCollection collection) {
        return DiscoveryCollectionResponse.builder()
                .id(collection.getId())
                .slug(collection.getSlug())
                .title(collection.getTitle())
                .type(collection.getType())
                .ruleDefinition(collection.getRuleDefinition())
                .displayOrder(collection.getDisplayOrder())
                .maxItems(collection.getMaxItems())
                .minItemsToDisplay(collection.getMinItemsToDisplay())
                .active(collection.isActive())
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .build();
    }

    private DiscoveryCollectionItemResponse toItemResponse(DiscoveryCollectionItem item) {
        return DiscoveryCollectionItemResponse.builder()
                .id(item.getId())
                .collectionId(item.getCollection().getId())
                .bagId(item.getBag().getId())
                .bagName(item.getBag().getName())
                .pinnedOrder(item.getPinnedOrder())
                .addedAt(item.getAddedAt())
                .build();
    }
}
