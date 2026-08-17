ALTER TABLE set_watch
    ADD COLUMN ebay_scan_interval_minutes INTEGER NOT NULL DEFAULT 15,
    ADD COLUMN bricklink_scan_interval_minutes INTEGER NOT NULL DEFAULT 360;

UPDATE set_watch
SET ebay_scan_interval_minutes = scan_interval_minutes;

ALTER TABLE set_watch
    DROP COLUMN scan_interval_minutes;
