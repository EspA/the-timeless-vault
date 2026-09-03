CREATE TABLE order_tracking (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    tracking_number VARCHAR(128) NOT NULL,
    carrier VARCHAR(128),
    sort_order INTEGER NOT NULL DEFAULT 0,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, tracking_number)
);

CREATE INDEX idx_order_tracking_order ON order_tracking (order_id);

CREATE TABLE purchase_order_tracking (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    tracking_number VARCHAR(128) NOT NULL,
    carrier VARCHAR(32),
    sort_order INTEGER NOT NULL DEFAULT 0,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (purchase_order_id, tracking_number)
);

CREATE INDEX idx_purchase_order_tracking_po ON purchase_order_tracking (purchase_order_id);

INSERT INTO order_tracking (id, order_id, tracking_number, carrier, sort_order, created_at)
SELECT id, id, tracking_number, shipping_provider, 0, created_at
FROM orders
WHERE tracking_number IS NOT NULL AND btrim(tracking_number) <> '';

INSERT INTO purchase_order_tracking (id, purchase_order_id, tracking_number, carrier, sort_order, created_at)
SELECT id, id, tracking_number, carrier, 0, created_at
FROM purchase_order
WHERE tracking_number IS NOT NULL AND btrim(tracking_number) <> '';
