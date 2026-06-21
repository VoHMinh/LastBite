package com.LastBite.modules.bag.repository;

import com.LastBite.modules.bag.entity.BagDailyStock;
import com.LastBite.modules.bag.enums.DailyStockStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BagDailyStockRepository extends JpaRepository<BagDailyStock, UUID> {

    Optional<BagDailyStock> findByBagIdAndDate(UUID bagId, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s FROM BagDailyStock s
        JOIN FETCH s.bag b
        JOIN FETCH s.store st
        WHERE b.id = :bagId AND s.date = :date
    """)
    Optional<BagDailyStock> findByBagIdAndDateForUpdate(UUID bagId, LocalDate date);

    @Query("""
        SELECT s FROM BagDailyStock s
        JOIN FETCH s.bag b
        WHERE s.status = :status
          AND (s.date < :date OR (s.date = :date AND b.pickupEndTime <= :time))
          AND s.sold < s.quantity
    """)
    List<BagDailyStock> findStocksToExpire(DailyStockStatus status, LocalDate date, LocalTime time);

    @Modifying
    @Query("""
        UPDATE BagDailyStock s
        SET s.status = com.LastBite.modules.bag.enums.DailyStockStatus.SOLD_OUT
        WHERE s.id = :stockId
          AND s.sold + s.reserved >= s.quantity
          AND s.status = com.LastBite.modules.bag.enums.DailyStockStatus.ACTIVE
    """)
    int markSoldOutIfDepleted(UUID stockId);

    @Query(value = """
        WITH discovery AS (
            SELECT
                b.id AS "bagId",
                st.id AS "storeId",
                st.name AS "storeName",
                st.slug AS "storeSlug",
                st.address AS "storeAddress",
                st.logo_url AS "storeLogoUrl",
                st.cover_image_url AS "storeCoverImageUrl",
                st.avg_rating AS "storeAvgRating",
                st.total_ratings AS "storeTotalRatings",
                st.district AS district,
                st.city AS city,
                st.lat AS lat,
                st.lng AS lng,
                b.name AS name,
                b.description AS description,
                b.bag_type AS "bagType",
                b.diet_type AS "dietType",
                b.category AS category,
                b.bag_size AS "bagSize",
                array_to_string(b.photos, ',') AS photos,
                b.minimum_value AS "minimumValue",
                b.base_sale_price AS "baseSalePrice",
                b.dynamic_min_price AS "dynamicMinPrice",
                b.dynamic_max_price AS "dynamicMaxPrice",
                b.dynamic_pricing_enabled AS "dynamicPricingEnabled",
                b.platform_fee AS "platformFee",
                b.max_per_order AS "maxPerOrder",
                b.container_provided AS "containerProvided",
                b.carrier_bag_provided AS "carrierBagProvided",
                b.packaging_note AS "packagingNote",
                b.pickup_start_time AS "pickupStartTime",
                b.pickup_end_time AS "pickupEndTime",
                b.status AS status,
                s.id AS "dailyStockId",
                s.date AS "stockDate",
                s.quantity AS quantity,
                s.reserved AS reserved,
                s.sold AS sold,
                (s.quantity - s.reserved - s.sold) AS available,
                s.status AS "stockStatus",
                (6371 * acos(least(1, greatest(-1,
                    cos(radians(CAST(:lat AS double precision))) * cos(radians(st.lat)) *
                    cos(radians(st.lng) - radians(CAST(:lng AS double precision))) +
                    sin(radians(CAST(:lat AS double precision))) * sin(radians(st.lat))
                )))) AS "distanceKm"
            FROM bag_daily_stocks s
            JOIN surprise_bags b ON b.id = s.bag_id
            JOIN stores st ON st.id = s.store_id
            LEFT JOIN store_reliability_stats rs ON rs.store_id = st.id
            WHERE s.date = :date
              AND s.status IN ('ACTIVE', 'SOLD_OUT')
              AND b.status = 'ACTIVE'
              AND st.status = 'ACTIVE'
              AND st.verification_status = 'VERIFIED'
              AND (rs.suspended_until IS NULL OR rs.suspended_until <= NOW())
              AND b.pickup_end_time > :nowTime
              AND st.lat IS NOT NULL
              AND st.lng IS NOT NULL
              AND (:category IS NULL OR b.category = :category)
              AND (:dietType IS NULL OR b.diet_type = :dietType)
              AND (:bagType IS NULL OR b.bag_type = :bagType)
              AND (:district IS NULL OR LOWER(st.district) = LOWER(:district))
        )
        SELECT * FROM discovery
        WHERE "distanceKm" <= :radiusKm
        ORDER BY
            CASE WHEN available <= 0 THEN 1 ELSE 0 END ASC,
            CASE WHEN :sort = 'distance' THEN "distanceKm" END ASC NULLS LAST,
            CASE WHEN :sort = 'price' THEN "baseSalePrice" END ASC,
            CASE WHEN :sort = 'pickup_time' THEN "pickupStartTime" END ASC,
            "pickupStartTime" ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<BagDiscoveryProjection> discoverWithLocation(@Param("date") LocalDate date,
                                                      @Param("nowTime") LocalTime nowTime,
                                                      @Param("lat") double lat,
                                                      @Param("lng") double lng,
                                                      @Param("radiusKm") double radiusKm,
                                                      @Param("category") String category,
                                                      @Param("dietType") String dietType,
                                                      @Param("bagType") String bagType,
                                                      @Param("district") String district,
                                                      @Param("sort") String sort,
                                                      @Param("limit") int limit);

    @Query(value = """
        SELECT
            b.id AS "bagId",
            st.id AS "storeId",
            st.name AS "storeName",
            st.slug AS "storeSlug",
            st.address AS "storeAddress",
            st.logo_url AS "storeLogoUrl",
            st.cover_image_url AS "storeCoverImageUrl",
            st.avg_rating AS "storeAvgRating",
            st.total_ratings AS "storeTotalRatings",
            st.district AS district,
            st.city AS city,
            st.lat AS lat,
            st.lng AS lng,
            b.name AS name,
            b.description AS description,
            b.bag_type AS "bagType",
            b.diet_type AS "dietType",
            b.category AS category,
            b.bag_size AS "bagSize",
            array_to_string(b.photos, ',') AS photos,
            b.minimum_value AS "minimumValue",
            b.base_sale_price AS "baseSalePrice",
            b.dynamic_min_price AS "dynamicMinPrice",
            b.dynamic_max_price AS "dynamicMaxPrice",
            b.dynamic_pricing_enabled AS "dynamicPricingEnabled",
            b.platform_fee AS "platformFee",
            b.max_per_order AS "maxPerOrder",
            b.container_provided AS "containerProvided",
            b.carrier_bag_provided AS "carrierBagProvided",
            b.packaging_note AS "packagingNote",
            b.pickup_start_time AS "pickupStartTime",
            b.pickup_end_time AS "pickupEndTime",
            b.status AS status,
            s.id AS "dailyStockId",
            s.date AS "stockDate",
            s.quantity AS quantity,
            s.reserved AS reserved,
            s.sold AS sold,
            (s.quantity - s.reserved - s.sold) AS available,
            s.status AS "stockStatus",
            CAST(NULL AS double precision) AS "distanceKm"
        FROM bag_daily_stocks s
        JOIN surprise_bags b ON b.id = s.bag_id
        JOIN stores st ON st.id = s.store_id
        LEFT JOIN store_reliability_stats rs ON rs.store_id = st.id
        WHERE s.date = :date
          AND s.status IN ('ACTIVE', 'SOLD_OUT')
          AND b.status = 'ACTIVE'
          AND st.status = 'ACTIVE'
          AND st.verification_status = 'VERIFIED'
          AND (rs.suspended_until IS NULL OR rs.suspended_until <= NOW())
          AND b.pickup_end_time > :nowTime
          AND (:category IS NULL OR b.category = :category)
          AND (:dietType IS NULL OR b.diet_type = :dietType)
          AND (:bagType IS NULL OR b.bag_type = :bagType)
          AND (:district IS NULL OR LOWER(st.district) = LOWER(:district))
        ORDER BY
            CASE WHEN (s.quantity - s.reserved - s.sold) <= 0 THEN 1 ELSE 0 END ASC,
            CASE WHEN :sort = 'price' THEN b.base_sale_price END ASC,
            b.pickup_start_time ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<BagDiscoveryProjection> discoverWithoutLocation(@Param("date") LocalDate date,
                                                         @Param("nowTime") LocalTime nowTime,
                                                         @Param("category") String category,
                                                         @Param("dietType") String dietType,
                                                         @Param("bagType") String bagType,
                                                         @Param("district") String district,
                                                         @Param("sort") String sort,
                                                         @Param("limit") int limit);

    @Query(value = """
        SELECT
            b.id AS "bagId",
            st.id AS "storeId",
            st.name AS "storeName",
            st.slug AS "storeSlug",
            st.address AS "storeAddress",
            st.logo_url AS "storeLogoUrl",
            st.cover_image_url AS "storeCoverImageUrl",
            st.avg_rating AS "storeAvgRating",
            st.total_ratings AS "storeTotalRatings",
            st.district AS district,
            st.city AS city,
            st.lat AS lat,
            st.lng AS lng,
            b.name AS name,
            b.description AS description,
            b.bag_type AS "bagType",
            b.diet_type AS "dietType",
            b.category AS category,
            b.bag_size AS "bagSize",
            array_to_string(b.photos, ',') AS photos,
            b.minimum_value AS "minimumValue",
            b.base_sale_price AS "baseSalePrice",
            b.dynamic_min_price AS "dynamicMinPrice",
            b.dynamic_max_price AS "dynamicMaxPrice",
            b.dynamic_pricing_enabled AS "dynamicPricingEnabled",
            b.platform_fee AS "platformFee",
            b.max_per_order AS "maxPerOrder",
            b.container_provided AS "containerProvided",
            b.carrier_bag_provided AS "carrierBagProvided",
            b.packaging_note AS "packagingNote",
            b.pickup_start_time AS "pickupStartTime",
            b.pickup_end_time AS "pickupEndTime",
            b.status AS status,
            s.id AS "dailyStockId",
            s.date AS "stockDate",
            s.quantity AS quantity,
            s.reserved AS reserved,
            s.sold AS sold,
            (s.quantity - s.reserved - s.sold) AS available,
            s.status AS "stockStatus",
            CAST(NULL AS double precision) AS "distanceKm"
        FROM bag_daily_stocks s
        JOIN surprise_bags b ON b.id = s.bag_id
        JOIN stores st ON st.id = s.store_id
        LEFT JOIN store_reliability_stats rs ON rs.store_id = st.id
        WHERE b.id = :bagId
          AND s.date = :date
          AND s.status IN ('ACTIVE', 'SOLD_OUT')
          AND b.status = 'ACTIVE'
          AND st.status = 'ACTIVE'
          AND st.verification_status = 'VERIFIED'
          AND (rs.suspended_until IS NULL OR rs.suspended_until <= NOW())
          AND b.pickup_end_time > :nowTime
        LIMIT 1
    """, nativeQuery = true)
    Optional<BagDiscoveryProjection> findPublicBagDetail(@Param("bagId") UUID bagId,
                                                         @Param("date") LocalDate date,
                                                         @Param("nowTime") LocalTime nowTime);

    @Query(value = """
        SELECT
            b.id AS "bagId",
            st.id AS "storeId",
            st.name AS "storeName",
            st.slug AS "storeSlug",
            st.address AS "storeAddress",
            st.logo_url AS "storeLogoUrl",
            st.cover_image_url AS "storeCoverImageUrl",
            st.avg_rating AS "storeAvgRating",
            st.total_ratings AS "storeTotalRatings",
            st.district AS district,
            st.city AS city,
            st.lat AS lat,
            st.lng AS lng,
            b.name AS name,
            b.description AS description,
            b.bag_type AS "bagType",
            b.diet_type AS "dietType",
            b.category AS category,
            b.bag_size AS "bagSize",
            array_to_string(b.photos, ',') AS photos,
            b.minimum_value AS "minimumValue",
            b.base_sale_price AS "baseSalePrice",
            b.dynamic_min_price AS "dynamicMinPrice",
            b.dynamic_max_price AS "dynamicMaxPrice",
            b.dynamic_pricing_enabled AS "dynamicPricingEnabled",
            b.platform_fee AS "platformFee",
            b.max_per_order AS "maxPerOrder",
            b.container_provided AS "containerProvided",
            b.carrier_bag_provided AS "carrierBagProvided",
            b.packaging_note AS "packagingNote",
            b.pickup_start_time AS "pickupStartTime",
            b.pickup_end_time AS "pickupEndTime",
            b.status AS status,
            s.id AS "dailyStockId",
            s.date AS "stockDate",
            s.quantity AS quantity,
            s.reserved AS reserved,
            s.sold AS sold,
            (s.quantity - s.reserved - s.sold) AS available,
            s.status AS "stockStatus",
            CAST(NULL AS double precision) AS "distanceKm"
        FROM bag_daily_stocks s
        JOIN surprise_bags b ON b.id = s.bag_id
        JOIN stores st ON st.id = s.store_id
        LEFT JOIN store_reliability_stats rs ON rs.store_id = st.id
        WHERE st.id = :storeId
          AND s.date = :date
          AND s.status IN ('ACTIVE', 'SOLD_OUT')
          AND b.status = 'ACTIVE'
          AND st.status = 'ACTIVE'
          AND st.verification_status = 'VERIFIED'
          AND (rs.suspended_until IS NULL OR rs.suspended_until <= NOW())
          AND b.pickup_end_time > :nowTime
        ORDER BY
            CASE WHEN (s.quantity - s.reserved - s.sold) <= 0 THEN 1 ELSE 0 END ASC,
            b.pickup_start_time ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<BagDiscoveryProjection> findPublicStoreBags(@Param("storeId") UUID storeId,
                                                     @Param("date") LocalDate date,
                                                     @Param("nowTime") LocalTime nowTime,
                                                     @Param("limit") int limit);
    @Query(value = """
        WITH orders_today AS (
            SELECT bag_id, CAST(COUNT(*) AS integer) AS orders_today_count
            FROM orders
            WHERE pickup_date = :date
              AND paid_at IS NOT NULL
              AND status NOT IN ('CANCELLED', 'REFUNDED')
            GROUP BY bag_id
        ), discovery AS (
            SELECT
                b.id AS "bagId",
                st.id AS "storeId",
                st.name AS "storeName",
                st.slug AS "storeSlug",
                st.address AS "storeAddress",
                st.logo_url AS "storeLogoUrl",
                st.cover_image_url AS "storeCoverImageUrl",
                st.avg_rating AS "storeAvgRating",
                st.total_ratings AS "storeTotalRatings",
                st.district AS district,
                st.city AS city,
                st.lat AS lat,
                st.lng AS lng,
                st.created_at AS "storeCreatedAt",
                COALESCE(ot.orders_today_count, 0) AS "ordersTodayCount",
                b.name AS name,
                b.description AS description,
                b.bag_type AS "bagType",
                b.diet_type AS "dietType",
                b.category AS category,
                b.bag_size AS "bagSize",
                array_to_string(b.photos, ',') AS photos,
                b.minimum_value AS "minimumValue",
                b.base_sale_price AS "baseSalePrice",
                b.dynamic_min_price AS "dynamicMinPrice",
                b.dynamic_max_price AS "dynamicMaxPrice",
                b.dynamic_pricing_enabled AS "dynamicPricingEnabled",
                b.platform_fee AS "platformFee",
                b.max_per_order AS "maxPerOrder",
                b.container_provided AS "containerProvided",
                b.carrier_bag_provided AS "carrierBagProvided",
                b.packaging_note AS "packagingNote",
                b.pickup_start_time AS "pickupStartTime",
                b.pickup_end_time AS "pickupEndTime",
                b.status AS status,
                s.id AS "dailyStockId",
                s.date AS "stockDate",
                s.quantity AS quantity,
                s.reserved AS reserved,
                s.sold AS sold,
                (s.quantity - s.reserved - s.sold) AS available,
                s.status AS "stockStatus",
                (6371 * acos(least(1, greatest(-1,
                    cos(radians(CAST(:lat AS double precision))) * cos(radians(st.lat)) *
                    cos(radians(st.lng) - radians(CAST(:lng AS double precision))) +
                    sin(radians(CAST(:lat AS double precision))) * sin(radians(st.lat))
                )))) AS "distanceKm"
            FROM bag_daily_stocks s
            JOIN surprise_bags b ON b.id = s.bag_id
            JOIN stores st ON st.id = s.store_id
            LEFT JOIN orders_today ot ON ot.bag_id = b.id
            LEFT JOIN store_reliability_stats rs ON rs.store_id = st.id
            WHERE s.date = :date
              AND s.status IN ('ACTIVE', 'SOLD_OUT')
              AND b.status = 'ACTIVE'
              AND st.status = 'ACTIVE'
              AND st.verification_status = 'VERIFIED'
              AND (rs.suspended_until IS NULL OR rs.suspended_until <= NOW())
              AND b.pickup_end_time > :nowTime
              AND st.lat IS NOT NULL
              AND st.lng IS NOT NULL
              AND st.lat BETWEEN CAST(:lat AS double precision) - (:radiusKm / 111.0)
                             AND CAST(:lat AS double precision) + (:radiusKm / 111.0)
              AND st.lng BETWEEN CAST(:lng AS double precision) - (:radiusKm / (111.0 * greatest(0.1, cos(radians(CAST(:lat AS double precision))))))
                             AND CAST(:lng AS double precision) + (:radiusKm / (111.0 * greatest(0.1, cos(radians(CAST(:lat AS double precision))))))
              AND (:category IS NULL OR b.category = :category)
              AND (:dietType IS NULL OR b.diet_type = :dietType)
              AND (:bagType IS NULL OR b.bag_type = :bagType)
              AND (:district IS NULL OR LOWER(st.district) = LOWER(:district))
              AND (:keyword IS NULL OR LOWER(b.name) LIKE :keyword
                   OR LOWER(COALESCE(b.description, '')) LIKE :keyword
                   OR LOWER(st.name) LIKE :keyword)
        )
        SELECT * FROM discovery
        WHERE "distanceKm" <= :radiusKm
        ORDER BY
            CASE WHEN available <= 0 THEN 1 ELSE 0 END ASC,
            "pickupStartTime" ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<BagDiscoveryProjection> findDiscoveryCandidatesWithLocation(@Param("date") LocalDate date,
                                                                     @Param("nowTime") LocalTime nowTime,
                                                                     @Param("lat") double lat,
                                                                     @Param("lng") double lng,
                                                                     @Param("radiusKm") double radiusKm,
                                                                     @Param("category") String category,
                                                                     @Param("dietType") String dietType,
                                                                     @Param("bagType") String bagType,
                                                                     @Param("district") String district,
                                                                     @Param("keyword") String keyword,
                                                                     @Param("limit") int limit);

    @Query(value = """
        WITH orders_today AS (
            SELECT bag_id, CAST(COUNT(*) AS integer) AS orders_today_count
            FROM orders
            WHERE pickup_date = :date
              AND paid_at IS NOT NULL
              AND status NOT IN ('CANCELLED', 'REFUNDED')
            GROUP BY bag_id
        )
        SELECT
            b.id AS "bagId",
            st.id AS "storeId",
            st.name AS "storeName",
            st.slug AS "storeSlug",
            st.address AS "storeAddress",
            st.logo_url AS "storeLogoUrl",
            st.cover_image_url AS "storeCoverImageUrl",
            st.avg_rating AS "storeAvgRating",
            st.total_ratings AS "storeTotalRatings",
            st.district AS district,
            st.city AS city,
            st.lat AS lat,
            st.lng AS lng,
            st.created_at AS "storeCreatedAt",
            COALESCE(ot.orders_today_count, 0) AS "ordersTodayCount",
            b.name AS name,
            b.description AS description,
            b.bag_type AS "bagType",
            b.diet_type AS "dietType",
            b.category AS category,
            b.bag_size AS "bagSize",
            array_to_string(b.photos, ',') AS photos,
            b.minimum_value AS "minimumValue",
            b.base_sale_price AS "baseSalePrice",
            b.dynamic_min_price AS "dynamicMinPrice",
            b.dynamic_max_price AS "dynamicMaxPrice",
            b.dynamic_pricing_enabled AS "dynamicPricingEnabled",
            b.platform_fee AS "platformFee",
            b.max_per_order AS "maxPerOrder",
            b.container_provided AS "containerProvided",
            b.carrier_bag_provided AS "carrierBagProvided",
            b.packaging_note AS "packagingNote",
            b.pickup_start_time AS "pickupStartTime",
            b.pickup_end_time AS "pickupEndTime",
            b.status AS status,
            s.id AS "dailyStockId",
            s.date AS "stockDate",
            s.quantity AS quantity,
            s.reserved AS reserved,
            s.sold AS sold,
            (s.quantity - s.reserved - s.sold) AS available,
            s.status AS "stockStatus",
            CAST(NULL AS double precision) AS "distanceKm"
        FROM bag_daily_stocks s
        JOIN surprise_bags b ON b.id = s.bag_id
        JOIN stores st ON st.id = s.store_id
        LEFT JOIN orders_today ot ON ot.bag_id = b.id
        LEFT JOIN store_reliability_stats rs ON rs.store_id = st.id
        WHERE s.date = :date
          AND s.status IN ('ACTIVE', 'SOLD_OUT')
          AND b.status = 'ACTIVE'
          AND st.status = 'ACTIVE'
          AND st.verification_status = 'VERIFIED'
          AND (rs.suspended_until IS NULL OR rs.suspended_until <= NOW())
          AND b.pickup_end_time > :nowTime
          AND (:category IS NULL OR b.category = :category)
          AND (:dietType IS NULL OR b.diet_type = :dietType)
          AND (:bagType IS NULL OR b.bag_type = :bagType)
          AND (:district IS NULL OR LOWER(st.district) = LOWER(:district))
          AND (:keyword IS NULL OR LOWER(b.name) LIKE :keyword
               OR LOWER(COALESCE(b.description, '')) LIKE :keyword
               OR LOWER(st.name) LIKE :keyword)
        ORDER BY
            CASE WHEN (s.quantity - s.reserved - s.sold) <= 0 THEN 1 ELSE 0 END ASC,
            b.pickup_start_time ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<BagDiscoveryProjection> findDiscoveryCandidatesWithoutLocation(@Param("date") LocalDate date,
                                                                        @Param("nowTime") LocalTime nowTime,
                                                                        @Param("category") String category,
                                                                        @Param("dietType") String dietType,
                                                                        @Param("bagType") String bagType,
                                                                        @Param("district") String district,
                                                                        @Param("keyword") String keyword,
                                                                        @Param("limit") int limit);
}
