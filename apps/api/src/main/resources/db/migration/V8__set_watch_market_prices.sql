ALTER TABLE set_watch
    ADD COLUMN brickeconomy_fetched_at TIMESTAMPTZ,
    ADD COLUMN ebay_current_value_new NUMERIC(12, 2),
    ADD COLUMN ebay_scanned_at TIMESTAMPTZ,
    ADD COLUMN bricklink_current_value_new NUMERIC(12, 2),
    ADD COLUMN bricklink_scanned_at TIMESTAMPTZ;

UPDATE set_watch sw
SET brickeconomy_fetched_at = c.fetched_at
FROM catalog_item c
WHERE c.id = sw.catalog_item_id;

UPDATE set_watch sw
SET ebay_current_value_new = s.avg_price,
    ebay_scanned_at = s.scanned_at
FROM (
    SELECT DISTINCT ON (catalog_item_id)
        catalog_item_id,
        avg_price,
        scanned_at
    FROM market_snapshot
    WHERE platform = 'EBAY'
      AND condition IN ('ANY', 'NEW')
    ORDER BY catalog_item_id, CASE WHEN condition = 'NEW' THEN 0 ELSE 1 END, scanned_at DESC
) s
WHERE s.catalog_item_id = sw.catalog_item_id;

UPDATE set_watch sw
SET bricklink_current_value_new = s.avg_price,
    bricklink_scanned_at = s.scanned_at
FROM (
    SELECT DISTINCT ON (catalog_item_id)
        catalog_item_id,
        avg_price,
        scanned_at
    FROM market_snapshot
    WHERE platform = 'BRICKLINK'
      AND condition = 'N'
    ORDER BY catalog_item_id, scanned_at DESC
) s
WHERE s.catalog_item_id = sw.catalog_item_id;
