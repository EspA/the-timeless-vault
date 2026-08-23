ALTER TABLE sale RENAME TO orders;
ALTER TABLE sale_ignore RENAME TO order_ignore;

ALTER INDEX idx_sale_sold_at RENAME TO idx_orders_sold_at;

ALTER TABLE orders
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN tracking_number VARCHAR(128),
    ADD COLUMN shipping_provider VARCHAR(128),
    ADD COLUMN status_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
