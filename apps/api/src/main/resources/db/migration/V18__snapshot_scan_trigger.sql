ALTER TABLE market_snapshot
    ADD COLUMN scan_trigger VARCHAR(32);

UPDATE market_snapshot s
SET scan_trigger = l.scan_trigger
FROM market_scan_log l
WHERE s.catalog_item_id IS NOT DISTINCT FROM l.catalog_item_id
  AND s.platform = l.platform
  AND l.scan_trigger IS NOT NULL
  AND l.scanned_at >= s.scanned_at - INTERVAL '2 seconds'
  AND l.scanned_at <= s.scanned_at + INTERVAL '2 seconds';

UPDATE market_snapshot
SET scan_trigger = 'AUTOMATIC'
WHERE scan_trigger IS NULL;
