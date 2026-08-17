ALTER TABLE inventory_item
    ADD COLUMN ebay_price NUMERIC(12, 2),
    ADD COLUMN bricklink_price NUMERIC(12, 2),
    ADD COLUMN shopify_price NUMERIC(12, 2);

UPDATE inventory_item
SET ebay_price = price,
    bricklink_price = price,
    shopify_price = price;

ALTER TABLE inventory_item
    ALTER COLUMN ebay_price SET NOT NULL,
    ALTER COLUMN bricklink_price SET NOT NULL,
    ALTER COLUMN shopify_price SET NOT NULL;
