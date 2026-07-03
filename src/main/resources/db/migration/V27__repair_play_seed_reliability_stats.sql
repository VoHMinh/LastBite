-- Repair play-test reliability counters created by V26.
-- StoreReliabilityService recalculates fulfillment_rate as fulfilled / sold,
-- so seeded sold count must always cover fulfilled and no-show quantities.

WITH seed_stats AS (
    SELECT
        stats.store_id,
        GREATEST(
            stats.total_bags_sold,
            stats.total_bags_fulfilled + stats.total_bags_no_show + 25
        ) AS repaired_sold
    FROM store_reliability_stats stats
    JOIN stores ON stores.id = stats.store_id
    WHERE stores.slug LIKE 'lb-di-an-store-%'
)
UPDATE store_reliability_stats stats
SET
    total_bags_sold = seed_stats.repaired_sold,
    total_bags_listed = GREATEST(stats.total_bags_listed, seed_stats.repaired_sold + 25),
    fulfillment_rate = CASE
        WHEN seed_stats.repaired_sold <= 0 THEN 1.0
        ELSE LEAST(1.0, stats.total_bags_fulfilled::DOUBLE PRECISION / seed_stats.repaired_sold)
    END,
    last_recalculated_at = NOW(),
    updated_at = NOW()
FROM seed_stats
WHERE stats.store_id = seed_stats.store_id;
