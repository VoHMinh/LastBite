ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS pickup_qr_token_encrypted TEXT;
