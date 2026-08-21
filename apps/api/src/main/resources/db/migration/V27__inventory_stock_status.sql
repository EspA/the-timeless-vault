ALTER TABLE inventory_item
    ADD COLUMN stock_status VARCHAR(32) NOT NULL DEFAULT 'IN_STOCK';

ALTER TABLE inventory_item
    ALTER COLUMN stock_status SET DEFAULT 'IN_TRANSIT';

ALTER TABLE inventory_item
    ALTER COLUMN quantity SET DEFAULT 0;
