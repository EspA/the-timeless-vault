CREATE TABLE sale_ignore (
    id UUID PRIMARY KEY,
    platform VARCHAR(32) NOT NULL,
    external_order_id VARCHAR(128) NOT NULL,
    external_line_id VARCHAR(128) NOT NULL,
    ignored_at TIMESTAMPTZ NOT NULL,
    UNIQUE (platform, external_order_id, external_line_id)
);
