CREATE TABLE IF NOT EXISTS favorite_bags (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bag_id UUID NOT NULL REFERENCES surprise_bags(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, bag_id)
);

CREATE INDEX idx_favorite_bags_user ON favorite_bags(user_id);
