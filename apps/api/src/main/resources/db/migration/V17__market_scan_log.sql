CREATE TABLE market_scan_log (
    id UUID PRIMARY KEY,
    catalog_item_id UUID REFERENCES catalog_item (id) ON DELETE SET NULL,
    set_number VARCHAR(64) NOT NULL,
    set_name VARCHAR(512),
    platform VARCHAR(32) NOT NULL,
    scan_trigger VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    listing_count INTEGER,
    message TEXT,
    scanned_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_market_scan_log_scanned ON market_scan_log (scanned_at DESC);

INSERT INTO market_scan_log (
    id, catalog_item_id, set_number, set_name, platform, scan_trigger, status, listing_count, message, scanned_at
)
SELECT
    s.id,
    s.catalog_item_id,
    c.set_number,
    c.name,
    s.platform,
    NULL,
    'SUCCESS',
    s.listing_count,
    NULL,
    s.scanned_at
FROM market_snapshot s
JOIN catalog_item c ON c.id = s.catalog_item_id;
