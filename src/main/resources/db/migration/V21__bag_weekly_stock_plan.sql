ALTER TABLE surprise_bags
    ADD COLUMN IF NOT EXISTS weekly_stock_plan INTEGER[] NOT NULL DEFAULT ARRAY[0,0,0,0,0,0,0];

ALTER TABLE surprise_bags
    DROP CONSTRAINT IF EXISTS chk_surprise_bags_weekly_stock_plan;
ALTER TABLE surprise_bags
    ADD CONSTRAINT chk_surprise_bags_weekly_stock_plan CHECK (
        array_length(weekly_stock_plan, 1) = 7
        AND weekly_stock_plan <@ ARRAY[0,1,2,3,4,5,6,7,8,9,10,
                                      11,12,13,14,15,16,17,18,19,20,
                                      21,22,23,24,25,26,27,28,29,30,
                                      31,32,33,34,35,36,37,38,39,40,
                                      41,42,43,44,45,46,47,48,49,50]
    );

ALTER TABLE bag_daily_stocks
    ADD COLUMN IF NOT EXISTS stock_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE bag_daily_stocks
    DROP CONSTRAINT IF EXISTS chk_bag_daily_stocks_stock_source;
ALTER TABLE bag_daily_stocks
    ADD CONSTRAINT chk_bag_daily_stocks_stock_source CHECK (
        stock_source IN ('WEEKLY_DEFAULT', 'MANUAL')
    );
