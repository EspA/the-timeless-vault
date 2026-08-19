CREATE TABLE listing_log (
    id UUID PRIMARY KEY,
    inventory_item_id UUID REFERENCES inventory_item (id) ON DELETE SET NULL,
    sku VARCHAR(128),
    set_number VARCHAR(64),
    item_title VARCHAR(512),
    platform VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message TEXT,
    logged_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_listing_log_logged ON listing_log (logged_at DESC);
