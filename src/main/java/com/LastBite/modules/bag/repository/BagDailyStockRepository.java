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
            WHERE s.date = :date
              AND s.status IN ('ACTIVE', 'SOLD_OUT')
              AND b.status = 'ACTIVE'
              AND st.status = 'ACTIVE'
              AND st.verification_status = 'VERIFIED'
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
        WHERE s.date = :date
          AND s.status IN ('ACTIVE', 'SOLD_OUT')
          AND b.status = 'ACTIVE'
          AND st.status = 'ACTIVE'
          AND st.verification_status = 'VERIFIED'
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
        WHERE b.id = :bagId
          AND s.date = :date
          AND s.status IN ('ACTIVE', 'SOLD_OUT')
          AND b.status = 'ACTIVE'
          AND st.status = 'ACTIVE'
          AND st.verification_status = 'VERIFIED'
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
        WHERE st.id = :storeId
          AND s.date = :date
          AND s.status IN ('ACTIVE', 'SOLD_OUT')
          AND b.status = 'ACTIVE'
          AND st.status = 'ACTIVE'
          AND st.verification_status = 'VERIFIED'
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
}

