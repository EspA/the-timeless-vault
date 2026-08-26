CREATE SEQUENCE purchase_order_number_seq START WITH 1;

CREATE TABLE supplier (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(64),
    website VARCHAR(512),
    street VARCHAR(255),
    city VARCHAR(128),
    zip VARCHAR(32),
    country VARCHAR(2),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE purchase_order (
    id UUID PRIMARY KEY,
    po_number INTEGER NOT NULL UNIQUE DEFAULT nextval('purchase_order_number_seq'),
    supplier_id UUID NOT NULL REFERENCES supplier (id),
    status VARCHAR(32) NOT NULL,
    expected_arrival DATE,
    tracking_number VARCHAR(128),
    carrier VARCHAR(32),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_purchase_order_status ON purchase_order (status);
CREATE INDEX idx_purchase_order_supplier ON purchase_order (supplier_id);
CREATE INDEX idx_purchase_order_updated ON purchase_order (updated_at DESC);

CREATE TABLE purchase_order_line (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    set_number VARCHAR(64) NOT NULL,
    title VARCHAR(80) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_value NUMERIC(12, 2) NOT NULL,
    inventory_item_id UUID REFERENCES inventory_item (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_purchase_order_line_po ON purchase_order_line (purchase_order_id);
CREATE INDEX idx_purchase_order_line_item ON purchase_order_line (inventory_item_id);
