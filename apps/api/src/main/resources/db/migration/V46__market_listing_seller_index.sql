CREATE INDEX IF NOT EXISTS idx_market_listing_platform_seller_lower
    ON market_listing (platform, lower(seller));
