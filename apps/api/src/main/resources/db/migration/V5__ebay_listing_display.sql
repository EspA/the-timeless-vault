ALTER TABLE market_listing
    ADD COLUMN image_url TEXT,
    ADD COLUMN seller_feedback_score INTEGER,
    ADD COLUMN seller_feedback_percentage VARCHAR(16),
    ADD COLUMN auction BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN current_bid NUMERIC(12, 2),
    ADD COLUMN best_offer BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN shipping_cost NUMERIC(12, 2),
    ADD COLUMN shipping_calculated BOOLEAN NOT NULL DEFAULT FALSE;
