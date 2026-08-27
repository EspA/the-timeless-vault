CREATE SEQUENCE quote_number_seq START WITH 1;

CREATE TABLE quote (
    id UUID PRIMARY KEY,
    quote_number INTEGER NOT NULL UNIQUE DEFAULT nextval('quote_number_seq'),
    shipping_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    purchase_order_id UUID REFERENCES purchase_order (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_quote_status ON quote (status);
CREATE INDEX idx_quote_updated ON quote (updated_at DESC);
CREATE INDEX idx_quote_purchase_order ON quote (purchase_order_id);

CREATE TABLE quote_line (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL REFERENCES quote (id) ON DELETE CASCADE,
    catalog_item_id UUID NOT NULL REFERENCES catalog_item (id),
    set_number VARCHAR(64) NOT NULL,
    title VARCHAR(80) NOT NULL,
    cost NUMERIC(12, 2) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_quote_line_quote ON quote_line (quote_id);
CREATE INDEX idx_quote_line_catalog ON quote_line (catalog_item_id);
