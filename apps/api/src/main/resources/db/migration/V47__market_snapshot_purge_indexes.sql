CREATE INDEX idx_market_snapshot_scanned_at
    ON market_snapshot (scanned_at);

CREATE INDEX idx_market_snapshot_latest
    ON market_snapshot (catalog_item_id, platform, condition, scanned_at DESC);
