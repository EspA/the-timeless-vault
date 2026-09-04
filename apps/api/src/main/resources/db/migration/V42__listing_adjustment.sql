CREATE TABLE listing_adjustment (
    id UUID PRIMARY KEY,
    channel_listing_id UUID NOT NULL UNIQUE REFERENCES channel_listing (id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    dismissed_at TIMESTAMPTZ,
    your_price NUMERIC(12, 2),
    market_price NUMERIC(12, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_listing_adjustment_status_detected ON listing_adjustment (status, detected_at DESC);

INSERT INTO listing_adjustment (
    id, channel_listing_id, type, status, detected_at, created_at, updated_at
)
SELECT DISTINCT ON (channel_listing_id)
    id,
    channel_listing_id,
    type,
    'ACTIVE',
    created_at,
    created_at,
    created_at
FROM alert_event
WHERE type IN ('PRICE_HIGH', 'PRICE_LOW')
  AND channel_listing_id IS NOT NULL
ORDER BY channel_listing_id, created_at DESC;
