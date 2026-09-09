CREATE INDEX IF NOT EXISTS idx_market_snapshot_catalog_platform_scanned
    ON market_snapshot (catalog_item_id, platform, scanned_at DESC);
