ALTER TABLE inventory_item
    ADD COLUMN brickowl_price NUMERIC(12, 2);

UPDATE inventory_item
SET brickowl_price = COALESCE(bricklink_price, price);

ALTER TABLE inventory_item
    ALTER COLUMN brickowl_price SET NOT NULL;

ALTER TABLE channel_listing
    ADD COLUMN brickowl_status VARCHAR(32);
