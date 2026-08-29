CREATE TABLE order_line (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    inventory_item_id UUID REFERENCES inventory_item (id) ON DELETE SET NULL,
    external_line_id VARCHAR(128) NOT NULL,
    sku VARCHAR(128),
    set_number VARCHAR(64),
    item_title VARCHAR(512),
    quantity INT NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    inventory_created BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (order_id, external_line_id)
);

CREATE INDEX idx_order_line_order ON order_line (order_id);
CREATE INDEX idx_order_line_item ON order_line (inventory_item_id);

INSERT INTO order_line (
    id,
    order_id,
    inventory_item_id,
    external_line_id,
    sku,
    set_number,
    item_title,
    quantity,
    unit_price,
    inventory_created,
    created_at
)
SELECT
    id,
    id,
    inventory_item_id,
    external_line_id,
    sku,
    set_number,
    item_title,
    quantity,
    unit_price,
    inventory_created,
    created_at
FROM orders;

CREATE TEMP TABLE order_header_keep AS
SELECT DISTINCT ON (platform, external_order_id) id
FROM orders
ORDER BY platform, external_order_id, created_at ASC, id ASC;

CREATE TEMP TABLE order_header_drop AS
SELECT id
FROM orders
WHERE id NOT IN (SELECT id FROM order_header_keep);

UPDATE order_line AS line
SET order_id = keeper.id
FROM orders AS extra
JOIN orders AS keeper
    ON keeper.platform = extra.platform
    AND keeper.external_order_id = extra.external_order_id
WHERE extra.id IN (SELECT id FROM order_header_drop)
  AND keeper.id IN (SELECT id FROM order_header_keep)
  AND line.order_id = extra.id;

UPDATE orders AS header
SET shipping_cost = totals.shipping_cost,
    platform_fee = totals.platform_fee
FROM (
    SELECT platform, external_order_id,
           SUM(shipping_cost) AS shipping_cost,
           SUM(platform_fee) AS platform_fee
    FROM orders
    GROUP BY platform, external_order_id
) AS totals
WHERE header.id IN (SELECT id FROM order_header_keep)
  AND header.platform = totals.platform
  AND header.external_order_id = totals.external_order_id;

DELETE FROM orders
WHERE id IN (SELECT id FROM order_header_drop);

ALTER TABLE orders DROP CONSTRAINT IF EXISTS sale_platform_external_order_id_external_line_id_key;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_platform_external_order_id_external_line_id_key;

ALTER TABLE orders
    DROP COLUMN inventory_item_id,
    DROP COLUMN sku,
    DROP COLUMN set_number,
    DROP COLUMN item_title,
    DROP COLUMN quantity,
    DROP COLUMN unit_price,
    DROP COLUMN inventory_created,
    DROP COLUMN external_line_id;

ALTER TABLE orders
    ADD CONSTRAINT orders_platform_external_order_id_key UNIQUE (platform, external_order_id);
