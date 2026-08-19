ALTER TABLE alert_event
    ADD COLUMN scan_trigger VARCHAR(32);

UPDATE alert_event a
SET scan_trigger = src.scan_trigger
FROM (
    SELECT DISTINCT ON (a2.id)
        a2.id,
        s.scan_trigger
    FROM alert_event a2
    JOIN market_listing ml
      ON ml.catalog_item_id = a2.catalog_item_id
     AND ml.platform = a2.platform
     AND ml.fingerprint = substring(a2.dedupe_key FROM '[^:]+$')
    JOIN market_snapshot s ON s.id = ml.snapshot_id
    WHERE a2.type = 'NEW_LISTING'
      AND s.scan_trigger IS NOT NULL
    ORDER BY a2.id, abs(extract(epoch FROM (ml.created_at - a2.created_at)))
) src
WHERE a.id = src.id;

UPDATE alert_event a
SET scan_trigger = src.scan_trigger
FROM (
    SELECT DISTINCT ON (a2.id)
        a2.id,
        l.scan_trigger
    FROM alert_event a2
    JOIN market_scan_log l
      ON l.catalog_item_id IS NOT DISTINCT FROM a2.catalog_item_id
     AND l.platform = a2.platform
    WHERE a2.type = 'NEW_LISTING'
      AND a2.scan_trigger IS NULL
      AND l.scan_trigger IS NOT NULL
      AND l.scanned_at BETWEEN a2.created_at - INTERVAL '5 seconds'
                           AND a2.created_at + INTERVAL '3 minutes'
    ORDER BY a2.id, abs(extract(epoch FROM (l.scanned_at - a2.created_at)))
) src
WHERE a.id = src.id;

UPDATE alert_event
SET scan_trigger = 'MANUAL'
WHERE type = 'NEW_LISTING'
  AND scan_trigger IS NULL;

ALTER TABLE alert_event
    DROP CONSTRAINT alert_event_dedupe_key_key;

CREATE UNIQUE INDEX alert_event_automatic_dedupe_key
    ON alert_event (dedupe_key)
    WHERE scan_trigger = 'AUTOMATIC';

CREATE UNIQUE INDEX alert_event_non_automatic_dedupe_key
    ON alert_event (dedupe_key)
    WHERE scan_trigger IS DISTINCT FROM 'AUTOMATIC';
