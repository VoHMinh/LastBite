package com.LastBite.modules.review.service;

import com.LastBite.common.exception.ApiException;
import com.LastBite.common.exception.ErrorCode;
import com.LastBite.modules.audit.service.AdminAuditLogService;
import com.LastBite.modules.auth.entity.User;
import com.LastBite.modules.auth.repository.UserRepository;
import com.LastBite.modules.media.entity.MediaUpload;
import com.LastBite.modules.media.enums.MediaPurpose;
import com.LastBite.modules.media.enums.MediaTargetType;
import com.LastBite.modules.media.enums.MediaUploadStatus;
import com.LastBite.modules.media.repository.MediaUploadRepository;
import com.LastBite.modules.media.service.MediaUrlService;
import com.LastBite.modules.order.entity.Order;
import com.LastBite.modules.order.enums.OrderStatus;
import com.LastBite.modules.order.repository.OrderRepository;
import com.LastBite.modules.review.dto.request.CreateReviewRequest;
import com.LastBite.modules.review.dto.request.ReportReviewRequest;
import com.LastBite.modules.review.dto.request.ResolveReviewReportRequest;
import com.LastBite.modules.review.dto.response.ReviewReportResponse;
import com.LastBite.modules.review.dto.response.ReviewResponse;
import com.LastBite.modules.review.dto.response.StoreRatingSummaryResponse;
import com.LastBite.modules.review.entity.Review;
import com.LastBite.modules.review.entity.ReviewPhoto;
import com.LastBite.modules.review.entity.ReviewReport;
import com.LastBite.modules.review.entity.StoreRatingSummary;
import com.LastBite.modules.review.enums.ReviewReportStatus;
import com.LastBite.modules.review.repository.ReviewPhotoRepository;
import com.LastBite.modules.review.repository.ReviewReportRepository;
import com.LastBite.modules.review.repository.ReviewRepository;
import com.LastBite.modules.review.repository.StoreRatingSummaryRepository;
import com.LastBite.modules.store.entity.Store;
import com.LastBite.modules.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Duration REVIEW_WINDOW = Duration.ofDays(14);
    private static final Duration RECENT_WINDOW = Duration.ofDays(90);

    private final ReviewRepository reviewRepository;
    private final ReviewPhotoRepository reviewPhotoRepository;
    private final ReviewReportRepository reportRepository;
    private final StoreRatingSummaryRepository summaryRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final MediaUploadRepository mediaUploadRepository;
    private final MediaUrlService mediaUrlService;
    private final AdminAuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public ReviewResponse create(UUID userId, UUID orderId, CreateReviewRequest request) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PICKED_UP) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Chi co the danh gia sau khi da nhan hang");
        }
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "Don hang da co danh gia");
        }
        Instant pickedUpAt = order.getPickedUpAt();
        if (pickedUpAt == null || pickedUpAt.plus(REVIEW_WINDOW).isBefore(Instant.now(clock))) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Da qua han 14 ngay de danh gia");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        Review review = reviewRepository.save(Review.builder()
                .order(order)
                .user(user)
                .store(order.getStore())
                .bag(order.getBag())
                .overallRating(request.getOverallRating())
                .collectionRating(request.getCollectionRating())
                .qualityRating(request.getQualityRating())
                .varietyRating(request.getVarietyRating())
                .quantityRating(request.getQuantityRating())
                .comment(trimToNull(request.getComment()))
                .visible(true)
                .build());

        List<ReviewPhoto> photos = attachPhotos(userId, review, request.getPhotoIds());
        refreshSummary(review.getStore());
        return toResponse(review, photos);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listStoreReviews(UUID storeId) {
        return reviewRepository.findByStoreIdAndVisibleTrueOrderByCreatedAtDesc(storeId).stream()
                .map(review -> toResponse(review, reviewPhotoRepository.findAllByReviewId(review.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getBagReviews(UUID bagId) {
        return reviewRepository.findByBagIdAndVisibleTrueOrderByCreatedAtDesc(bagId).stream()
                .map(review -> toResponse(review, reviewPhotoRepository.findAllByReviewId(review.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse getByOrderId(UUID orderId) {
        return reviewRepository.findByOrderId(orderId)
                .map(review -> toResponse(review, reviewPhotoRepository.findAllByReviewId(review.getId())))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public StoreRatingSummaryResponse getStoreRatingSummary(UUID storeId) {
        StoreRatingSummary summary = summaryRepository.findById(storeId)
                .orElseGet(() -> emptySummary(storeRepository.findById(storeId)
                        .orElseThrow(() -> new ApiException(ErrorCode.STORE_NOT_FOUND))));
        return toSummaryResponse(summary);
    }

    @Transactional
    public ReviewReportResponse report(UUID userId, UUID reviewId, ReportReviewRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay review"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        ReviewReport report = reportRepository.save(ReviewReport.builder()
                .review(review)
                .reportedBy(user)
                .reason(request.getReason().trim())
                .status(ReviewReportStatus.PENDING)
                .resolutionNote(trimToNull(request.getNote()))
                .build());
        return toReportResponse(report);
    }

    @Transactional(readOnly = true)
    public Page<ReviewReportResponse> listReports(ReviewReportStatus status, Pageable pageable) {
        Page<ReviewReport> reports = status == null
                ? reportRepository.findAll(pageable)
                : reportRepository.findAllByStatus(status, pageable);
        return reports.map(this::toReportResponse);
    }

    @Transactional
    public ReviewReportResponse resolveReport(UUID adminId, UUID reportId, ResolveReviewReportRequest request) {
        ReviewReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay review report"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (request.getStatus() == ReviewReportStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Trang thai xu ly report khong hop le");
        }
        report.setStatus(request.getStatus());
        report.setResolutionNote(trimToNull(request.getResolutionNote()));
        report.setResolvedBy(admin);
        report.setResolvedAt(Instant.now(clock));
        if (Boolean.TRUE.equals(request.getHideReview())) {
            hideReview(admin, report.getReview(), report.getResolutionNote());
        }
        auditLogService.record(admin, "REVIEW_REPORT_RESOLVE", "REVIEW_REPORT", report.getId(),
                report.getResolutionNote(), "status=" + report.getStatus());
        return toReportResponse(report);
    }

    @Transactional
    public ReviewResponse hide(UUID adminId, UUID reviewId, String reason) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay review"));
        hideReview(admin, review, reason);
        return toResponse(review, reviewPhotoRepository.findAllByReviewId(review.getId()));
    }

    private List<ReviewPhoto> attachPhotos(UUID userId, Review review, List<UUID> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return List.of();
        }
        List<ReviewPhoto> photos = new ArrayList<>();
        for (UUID photoId : photoIds) {
            MediaUpload media = mediaUploadRepository.findByIdAndOwnerId(photoId, userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Khong tim thay anh danh gia"));
            if (media.getStatus() != MediaUploadStatus.CONFIRMED || media.getPurpose() != MediaPurpose.FEEDBACK_IMAGE) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Anh danh gia khong hop le");
            }
            media.setTargetType(MediaTargetType.FEEDBACK);
            media.setTargetId(review.getId());
            photos.add(reviewPhotoRepository.save(ReviewPhoto.builder()
                    .review(review)
                    .mediaUpload(media)
                    .build()));
        }
        return photos;
    }

    private void hideReview(User admin, Review review, String reason) {
        review.setVisible(false);
        review.setHiddenReason(trimToNull(reason));
        refreshSummary(review.getStore());
        auditLogService.record(admin, "REVIEW_HIDE", "REVIEW", review.getId(),
                review.getHiddenReason(), "storeId=" + review.getStore().getId());
    }

    private void refreshSummary(Store store) {
        Instant recentAfter = Instant.now(clock).minus(RECENT_WINDOW);
        Object aggregate = reviewRepository.aggregateVisibleRatings(store.getId(), recentAfter);
        Object[] row = aggregate instanceof Object[] values ? values : new Object[]{aggregate};

        int reviewCount = numberAt(row, 0).intValue();
        StoreRatingSummary summary = summaryRepository.findById(store.getId())
                .orElseGet(() -> emptySummary(store));
        summary.setReviewCount(reviewCount);
        summary.setOverallRatingAvg(avgAt(row, 1));
        summary.setCollectionRatingAvg(avgAt(row, 2));
        summary.setQualityRatingAvg(avgAt(row, 3));
        summary.setVarietyRatingAvg(avgAt(row, 4));
        summary.setQuantityRatingAvg(avgAt(row, 5));
        summary.setRecentReviewCount(numberAt(row, 6).intValue());
        summary.setUpdatedAt(Instant.now(clock));
        summaryRepository.save(summary);

        store.setAvgRating(summary.getOverallRatingAvg().doubleValue());
        store.setTotalRatings(reviewCount);
    }

    private StoreRatingSummary emptySummary(Store store) {
        return StoreRatingSummary.builder()
                .storeId(store.getId())
                .reviewCount(0)
                .recentReviewCount(0)
                .overallRatingAvg(BigDecimal.ZERO)
                .collectionRatingAvg(BigDecimal.ZERO)
                .qualityRatingAvg(BigDecimal.ZERO)
                .varietyRatingAvg(BigDecimal.ZERO)
                .quantityRatingAvg(BigDecimal.ZERO)
                .updatedAt(Instant.now(clock))
                .build();
    }

    private Number numberAt(Object[] row, int index) {
        if (row.length <= index || row[index] == null) {
            return 0;
        }
        return (Number) row[index];
    }

    private BigDecimal avgAt(Object[] row, int index) {
        if (row.length <= index || row[index] == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(((Number) row[index]).doubleValue())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private ReviewResponse toResponse(Review review, List<ReviewPhoto> photos) {
        return ReviewResponse.builder()
                .id(review.getId())
                .orderId(review.getOrder().getId())
                .userId(review.getUser().getId())
                .storeId(review.getStore().getId())
                .bagId(review.getBag().getId())
                .overallRating(review.getOverallRating())
                .collectionRating(review.getCollectionRating())
                .qualityRating(review.getQualityRating())
                .varietyRating(review.getVarietyRating())
                .quantityRating(review.getQuantityRating())
                .comment(review.getComment())
                .visible(review.isVisible())
                .hiddenReason(review.getHiddenReason())
                .photoUrls(photos.stream()
                        .map(photo -> mediaUrlService.resolveUrl(
                                photo.getMediaUpload().getObjectKey(),
                                photo.getMediaUpload().getPublicUrl()))
                        .toList())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private StoreRatingSummaryResponse toSummaryResponse(StoreRatingSummary summary) {
        return StoreRatingSummaryResponse.builder()
                .storeId(summary.getStoreId())
                .reviewCount(summary.getReviewCount())
                .recentReviewCount(summary.getRecentReviewCount())
                .overallRatingAvg(summary.getOverallRatingAvg())
                .collectionRatingAvg(summary.getCollectionRatingAvg())
                .qualityRatingAvg(summary.getQualityRatingAvg())
                .varietyRatingAvg(summary.getVarietyRatingAvg())
                .quantityRatingAvg(summary.getQuantityRatingAvg())
                .updatedAt(summary.getUpdatedAt())
                .build();
    }

    private ReviewReportResponse toReportResponse(ReviewReport report) {
        return ReviewReportResponse.builder()
                .id(report.getId())
                .reviewId(report.getReview().getId())
                .reportedByUserId(report.getReportedBy().getId())
                .reason(report.getReason())
                .status(report.getStatus())
                .resolutionNote(report.getResolutionNote())
                .resolvedByUserId(report.getResolvedBy() == null ? null : report.getResolvedBy().getId())
                .resolvedAt(report.getResolvedAt())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
