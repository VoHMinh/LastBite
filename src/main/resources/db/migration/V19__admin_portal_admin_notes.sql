CREATE TABLE IF NOT EXISTS admin_notes (
    id UUID PRIMARY KEY,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    note TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_admin_notes_target ON admin_notes(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_admin_notes_actor ON admin_notes(actor_user_id);
