CREATE TABLE sale (
    id UUID PRIMARY KEY,
    inventory_item_id UUID REFERENCES inventory_item (id) ON DELETE SET NULL,
    platform VARCHAR(32) NOT NULL,
    external_order_id VARCHAR(128) NOT NULL,
    external_line_id VARCHAR(128) NOT NULL,
    sku VARCHAR(128),
    set_number VARCHAR(64),
    item_title VARCHAR(512),
    quantity INT NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    sold_at TIMESTAMPTZ NOT NULL,
    order_url VARCHAR(1024),
    inventory_created BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (platform, external_order_id, external_line_id)
);

CREATE INDEX idx_sale_sold_at ON sale (sold_at DESC);
