-- liquibase formatted sql

--changeset oleksandra:003-receipts-tables

CREATE TABLE receipts (
    id                    BIGSERIAL PRIMARY KEY,
    receipt_no            VARCHAR(20) NOT NULL,
    order_id              BIGINT,
    channel               VARCHAR(10) NOT NULL,
    closed_at             TIMESTAMPTZ NOT NULL,
    customer_id           BIGINT,
    customer_name         VARCHAR(150),
    customer_phone        VARCHAR(20),
    customer_discount_pct NUMERIC(4,2) NOT NULL DEFAULT 0,
    waiter_id             BIGINT,
    waiter_name           VARCHAR(150),
    table_id              INT,
    table_number          INT,
    hall                  VARCHAR(60),
    guests                INT,
    delivery_address      VARCHAR(250),
    courier_name          VARCHAR(150),
    eta_minutes           INT,
    subtotal              NUMERIC(12,2) NOT NULL,
    discount              NUMERIC(12,2) NOT NULL DEFAULT 0,
    total                 NUMERIC(12,2) NOT NULL,
    payment_method        VARCHAR(10) NOT NULL,
    paid_at               TIMESTAMPTZ NOT NULL,
    rating                INT,
    feedback_comment      TEXT,
    CONSTRAINT uq_receipts_receipt_no UNIQUE (receipt_no),
    CONSTRAINT chk_receipts_channel CHECK (channel IN ('DINE_IN', 'DELIVERY', 'TAKEAWAY')),
    CONSTRAINT chk_receipts_payment_method CHECK (payment_method IN ('CASH', 'CARD')),
    CONSTRAINT chk_receipts_totals_non_negative CHECK (subtotal >= 0 AND discount >= 0 AND total >= 0),
    CONSTRAINT chk_receipts_rating_range CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);

CREATE TABLE receipt_items (
    id         BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    line_no    INT NOT NULL,
    dish_id    BIGINT NOT NULL,
    dish_name  VARCHAR(120) NOT NULL,
    category   VARCHAR(80) NOT NULL,
    qty        INT NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    CONSTRAINT uq_receipt_items_line UNIQUE (receipt_id, line_no),
    CONSTRAINT chk_receipt_items_qty_positive CHECK (qty > 0),
    CONSTRAINT chk_receipt_items_price_positive CHECK (unit_price > 0)
);

CREATE TABLE receipt_item_modifiers (
    id              BIGSERIAL PRIMARY KEY,
    receipt_item_id BIGINT NOT NULL REFERENCES receipt_items (id) ON DELETE CASCADE,
    name            VARCHAR(80) NOT NULL,
    price_delta     NUMERIC(10,2) NOT NULL DEFAULT 0
);

CREATE TABLE receipt_tags (
    receipt_id BIGINT NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    tag        VARCHAR(40) NOT NULL,
    CONSTRAINT pk_receipt_tags PRIMARY KEY (receipt_id, tag)
);

--changeset oleksandra:003-receipts-indexes
CREATE INDEX idx_receipts_customer_closed_at ON receipts (customer_id, closed_at DESC);
CREATE INDEX idx_receipts_closed_at ON receipts (closed_at DESC);
CREATE INDEX idx_receipts_channel ON receipts (channel);
CREATE INDEX idx_receipt_items_receipt_id ON receipt_items (receipt_id);
CREATE INDEX idx_receipt_items_dish_id ON receipt_items (dish_id);
CREATE INDEX idx_receipt_item_modifiers_item_id ON receipt_item_modifiers (receipt_item_id);
CREATE INDEX idx_receipt_item_modifiers_name ON receipt_item_modifiers (name);
CREATE INDEX idx_receipt_tags_tag ON receipt_tags (tag);
