CREATE INDEX idx_market_listing_platform_seller_lower
    ON market_listing (platform, lower(seller));
