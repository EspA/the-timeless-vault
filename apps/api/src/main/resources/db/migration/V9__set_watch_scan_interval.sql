ALTER TABLE set_watch
    ADD COLUMN scan_interval_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN last_scanned_at TIMESTAMPTZ;

UPDATE set_watch
SET last_scanned_at = GREATEST(ebay_scanned_at, bricklink_scanned_at)
WHERE last_scanned_at IS NULL;
