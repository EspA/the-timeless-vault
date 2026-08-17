ALTER TABLE catalog_item
    ADD COLUMN retail_price_us NUMERIC(12, 2);

CREATE TABLE set_watch (
    id UUID PRIMARY KEY,
    catalog_item_id UUID NOT NULL UNIQUE REFERENCES catalog_item (id),
    set_number VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(512) NOT NULL,
    theme VARCHAR(255),
    subtheme VARCHAR(255),
    released_date DATE,
    retired_date DATE,
    retired BOOLEAN,
    pieces_count INTEGER,
    minifigs_count INTEGER,
    retail_price_us NUMERIC(12, 2),
    current_value_new NUMERIC(12, 2),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ebay_search_query TEXT,
    ebay_exclude_words TEXT,
    ebay_feedback_min INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO set_watch (
    id,
    catalog_item_id,
    set_number,
    name,
    theme,
    subtheme,
    released_date,
    retired_date,
    retired,
    pieces_count,
    minifigs_count,
    retail_price_us,
    current_value_new,
    enabled,
    ebay_search_query,
    ebay_exclude_words,
    ebay_feedback_min,
    created_at,
    updated_at
)
SELECT
    wr.id,
    c.id,
    c.set_number,
    c.name,
    c.theme,
    c.subtheme,
    c.released_date,
    c.retired_date,
    c.retired,
    c.pieces_count,
    c.minifigs_count,
    CASE
        WHEN c.brickeconomy_json->>'retail_price_us' ~ '^[0-9]+(\.[0-9]+)?$'
            THEN (c.brickeconomy_json->>'retail_price_us')::numeric
        ELSE NULL
    END,
    c.current_value_new,
    wr.enabled,
    wr.ebay_search_query,
    wr.ebay_exclude_words,
    COALESCE(wr.ebay_feedback_min, 1),
    wr.created_at,
    wr.updated_at
FROM watch_rule wr
JOIN catalog_item c ON c.id = wr.catalog_item_id;
