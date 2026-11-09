-- liquibase formatted sql

--changeset oleksandra:001-core-schema

CREATE TABLE halls (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    CONSTRAINT uq_halls_name UNIQUE (name)
);

CREATE TABLE restaurant_tables (
    id      SERIAL PRIMARY KEY,
    hall_id INT NOT NULL REFERENCES halls (id),
    number  INT NOT NULL,
    seats   INT NOT NULL,
    CONSTRAINT uq_restaurant_tables_hall_number UNIQUE (hall_id, number),
    CONSTRAINT chk_restaurant_tables_seats_positive CHECK (seats > 0)
);

CREATE TABLE menu_categories (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    CONSTRAINT uq_menu_categories_name UNIQUE (name)
);

CREATE TABLE dishes (
    id          BIGSERIAL PRIMARY KEY,
    category_id INT NOT NULL REFERENCES menu_categories (id),
    name        VARCHAR(120) NOT NULL,
    price       NUMERIC(10,2) NOT NULL,
    CONSTRAINT uq_dishes_category_name UNIQUE (category_id, name),
    CONSTRAINT chk_dishes_price_positive CHECK (price > 0)
);

CREATE TABLE employees (
    id        BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    job_title VARCHAR(60) NOT NULL
);

CREATE TABLE customers (
    id           BIGSERIAL PRIMARY KEY,
    full_name    VARCHAR(150) NOT NULL,
    phone        VARCHAR(20),
    discount_pct NUMERIC(4,2) NOT NULL DEFAULT 0,
    CONSTRAINT chk_customers_discount_range CHECK (discount_pct BETWEEN 0 AND 50)
);

CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    table_id    INT NOT NULL REFERENCES restaurant_tables (id),
    waiter_id   BIGINT NOT NULL REFERENCES employees (id),
    customer_id BIGINT REFERENCES customers (id),
    status      VARCHAR(15) NOT NULL DEFAULT 'OPEN',
    total       NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at   TIMESTAMPTZ,
    CONSTRAINT chk_orders_status CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    CONSTRAINT chk_orders_total_non_negative CHECK (total >= 0)
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    dish_id    BIGINT NOT NULL REFERENCES dishes (id),
    qty        INT NOT NULL,
    unit_price NUMERIC(10,2) NOT NULL,
    CONSTRAINT chk_order_items_qty_positive CHECK (qty > 0),
    CONSTRAINT chk_order_items_price_positive CHECK (unit_price > 0)
);

CREATE TABLE payments (
    id       BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders (id),
    method   VARCHAR(10) NOT NULL,
    amount   NUMERIC(12,2) NOT NULL,
    paid_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payments_method CHECK (method IN ('CASH', 'CARD')),
    CONSTRAINT chk_payments_amount_positive CHECK (amount > 0)
);

--changeset oleksandra:001-core-indexes
CREATE INDEX idx_restaurant_tables_hall_id ON restaurant_tables (hall_id);
CREATE INDEX idx_dishes_category_id ON dishes (category_id);
CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_waiter_id ON orders (waiter_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_dish_id ON order_items (dish_id);
CREATE INDEX idx_payments_order_id ON payments (order_id);
