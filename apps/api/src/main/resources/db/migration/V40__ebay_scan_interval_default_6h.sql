ALTER TABLE set_watch
    ALTER COLUMN ebay_scan_interval_minutes SET DEFAULT 360;

UPDATE set_watch
SET ebay_scan_interval_minutes = 360,
    updated_at = NOW();
