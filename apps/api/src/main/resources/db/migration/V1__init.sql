CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    google_sub VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    picture_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ
);

CREATE TABLE app_setting (
    key VARCHAR(128) PRIMARY KEY,
    value TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE catalog_item (
    id UUID PRIMARY KEY,
    set_number VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(512) NOT NULL,
    theme VARCHAR(255),
    subtheme VARCHAR(255),
    year INTEGER,
    pieces_count INTEGER,
    minifigs_count INTEGER,
    upc VARCHAR(64),
    ean VARCHAR(64),
    retired BOOLEAN,
    retired_date DATE,
    released_date DATE,
    current_value_new NUMERIC(12, 2),
    current_value_used NUMERIC(12, 2),
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    brickeconomy_json JSONB,
    fetched_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory_item (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item (id),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    price NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    cost NUMERIC(12, 2),
    item_type VARCHAR(32) NOT NULL,
    condition VARCHAR(32) NOT NULL,
    shopify_collection_ids TEXT,
    ebay_store_category VARCHAR(64),
    minimum_offer NUMERIC(12, 2),
    package_lbs INTEGER NOT NULL DEFAULT 0,
    package_oz INTEGER NOT NULL DEFAULT 0,
    package_length NUMERIC(8, 2),
    package_width NUMERIC(8, 2),
    package_height NUMERIC(8, 2),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE photo (
    id UUID PRIMARY KEY,
    inventory_item_id UUID NOT NULL REFERENCES inventory_item (id) ON DELETE CASCADE,
    storage_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(512),
    content_type VARCHAR(128),
    sort_order INTEGER NOT NULL DEFAULT 0,
    primary_for_bricklink BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE channel_listing (
    id UUID PRIMARY KEY,
    inventory_item_id UUID NOT NULL REFERENCES inventory_item (id) ON DELETE CASCADE,
    platform VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    external_id VARCHAR(255),
    live_url TEXT,
    last_published_price NUMERIC(12, 2),
    bricklink_photo_upload_url TEXT,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (inventory_item_id, platform)
);

CREATE TABLE publish_job (
    id UUID PRIMARY KEY,
    inventory_item_id UUID NOT NULL REFERENCES inventory_item (id) ON DELETE CASCADE,
    platform VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE watch_rule (
    id UUID PRIMARY KEY,
    catalog_item_id UUID NOT NULL UNIQUE REFERENCES catalog_item (id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    condition_filter VARCHAR(32) NOT NULL DEFAULT 'NEW',
    max_price NUMERIC(12, 2),
    title_keywords VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE price_guard (
    id UUID PRIMARY KEY,
    channel_listing_id UUID NOT NULL UNIQUE REFERENCES channel_listing (id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    high_percent NUMERIC(6, 2) NOT NULL DEFAULT 15,
    low_percent NUMERIC(6, 2) NOT NULL DEFAULT 15,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE market_snapshot (
    id UUID PRIMARY KEY,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item (id) ON DELETE CASCADE,
    platform VARCHAR(32) NOT NULL,
    condition VARCHAR(32) NOT NULL,
    min_price NUMERIC(12, 2),
    max_price NUMERIC(12, 2),
    avg_price NUMERIC(12, 2),
    listing_count INTEGER,
    scanned_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE market_listing (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES market_snapshot (id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item (id) ON DELETE CASCADE,
    platform VARCHAR(32) NOT NULL,
    external_id VARCHAR(255),
    fingerprint VARCHAR(255) NOT NULL,
    title TEXT,
    price NUMERIC(12, 2),
    quantity INTEGER,
    condition VARCHAR(64),
    seller VARCHAR(255),
    url TEXT,
    is_own BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE alert_event (
    id UUID PRIMARY KEY,
    catalog_item_id UUID REFERENCES catalog_item (id) ON DELETE CASCADE,
    channel_listing_id UUID REFERENCES channel_listing (id) ON DELETE SET NULL,
    type VARCHAR(64) NOT NULL,
    platform VARCHAR(32),
    title VARCHAR(512) NOT NULL,
    body TEXT,
    url TEXT,
    dedupe_key VARCHAR(255) NOT NULL UNIQUE,
    read_at TIMESTAMPTZ,
    emailed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_inventory_catalog ON inventory_item (catalog_item_id);
CREATE INDEX idx_photo_inventory ON photo (inventory_item_id);
CREATE INDEX idx_listing_inventory ON channel_listing (inventory_item_id);
CREATE INDEX idx_publish_job_item ON publish_job (inventory_item_id, created_at DESC);
CREATE INDEX idx_market_listing_fp ON market_listing (catalog_item_id, platform, fingerprint);
CREATE INDEX idx_market_snapshot_scan ON market_snapshot (catalog_item_id, scanned_at DESC);
CREATE INDEX idx_alert_created ON alert_event (created_at DESC);
