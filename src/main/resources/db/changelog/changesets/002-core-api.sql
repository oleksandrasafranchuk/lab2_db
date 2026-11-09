-- liquibase formatted sql

--changeset oleksandra:002-fn-customer-discount splitStatements:false
CREATE FUNCTION fn_customer_discount(p_customer BIGINT)
    RETURNS NUMERIC
    LANGUAGE plpgsql
    STABLE
AS
$$
DECLARE
    v_pct NUMERIC;
BEGIN
    IF p_customer IS NULL THEN
        RETURN 0;
    END IF;

    SELECT discount_pct INTO v_pct FROM customers WHERE id = p_customer;
    RETURN coalesce(v_pct, 0);
END
$$;

--changeset oleksandra:002-fn-order-total splitStatements:false
CREATE FUNCTION fn_order_total(p_order BIGINT)
    RETURNS NUMERIC
    LANGUAGE plpgsql
    STABLE
AS
$$
DECLARE
    v_total NUMERIC;
BEGIN
    SELECT coalesce(sum(qty * unit_price), 0) INTO v_total
    FROM order_items
    WHERE order_id = p_order;

    RETURN v_total;
END
$$;

--changeset oleksandra:002-trg-recalc-order-total splitStatements:false
CREATE FUNCTION trg_recalc_order_total()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_order BIGINT := coalesce(NEW.order_id, OLD.order_id);
    v_raw   NUMERIC;
    v_pct   NUMERIC;
BEGIN
    v_raw := fn_order_total(v_order);
    SELECT fn_customer_discount(customer_id) INTO v_pct FROM orders WHERE id = v_order;

    UPDATE orders
    SET total = round(v_raw * (1 - v_pct / 100), 2)
    WHERE id = v_order;

    RETURN NULL;
END
$$;

CREATE TRIGGER recalc_order_total
    AFTER INSERT OR UPDATE OR DELETE
    ON order_items
    FOR EACH ROW
EXECUTE FUNCTION trg_recalc_order_total();

--changeset oleksandra:002-views
CREATE VIEW v_orders AS
SELECT o.id,
       o.status,
       o.total,
       o.created_at,
       o.closed_at,
       o.table_id,
       t.number       AS table_number,
       h.name         AS hall,
       o.waiter_id,
       e.full_name    AS waiter,
       o.customer_id,
       c.full_name    AS customer,
       c.phone        AS customer_phone,
       coalesce(c.discount_pct, 0) AS customer_discount_pct,
       (SELECT count(*) FROM order_items oi WHERE oi.order_id = o.id) AS items_count
FROM orders o
         JOIN restaurant_tables t ON t.id = o.table_id
         JOIN halls h ON h.id = t.hall_id
         JOIN employees e ON e.id = o.waiter_id
         LEFT JOIN customers c ON c.id = o.customer_id;

CREATE VIEW v_order_details AS
SELECT oi.id    AS item_id,
       oi.order_id,
       oi.dish_id,
       d.name   AS dish,
       mc.name  AS category,
       oi.qty,
       oi.unit_price,
       oi.qty * oi.unit_price AS line_total
FROM order_items oi
         JOIN dishes d ON d.id = oi.dish_id
         JOIN menu_categories mc ON mc.id = d.category_id;

CREATE VIEW v_order_payments AS
SELECT p.id,
       p.order_id,
       p.method,
       p.amount,
       p.paid_at
FROM payments p;

--changeset oleksandra:002-sp-order-create splitStatements:false
CREATE PROCEDURE sp_order_create(p_table INT, p_waiter BIGINT, p_customer BIGINT, INOUT p_id BIGINT)
    LANGUAGE plpgsql
AS
$$
BEGIN
    INSERT INTO orders (table_id, waiter_id, customer_id)
    VALUES (p_table, p_waiter, p_customer)
    RETURNING id INTO p_id;
END
$$;

--changeset oleksandra:002-sp-order-add-item splitStatements:false
CREATE PROCEDURE sp_order_add_item(p_order BIGINT, p_dish BIGINT, p_qty INT, INOUT p_id BIGINT)
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_price NUMERIC;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM orders WHERE id = p_order AND status = 'OPEN') THEN
        RAISE EXCEPTION 'Order % is not open', p_order;
    END IF;

    SELECT price INTO v_price FROM dishes WHERE id = p_dish;
    IF v_price IS NULL THEN
        RAISE EXCEPTION 'Dish % not found', p_dish;
    END IF;

    INSERT INTO order_items (order_id, dish_id, qty, unit_price)
    VALUES (p_order, p_dish, p_qty, v_price)
    RETURNING id INTO p_id;
END
$$;

--changeset oleksandra:002-sp-order-pay splitStatements:false
CREATE PROCEDURE sp_order_pay(p_order BIGINT, p_method TEXT, INOUT p_id BIGINT)
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_total NUMERIC;
BEGIN
    SELECT total INTO v_total FROM orders WHERE id = p_order AND status = 'OPEN';
    IF v_total IS NULL THEN
        RAISE EXCEPTION 'Order % is not open', p_order;
    END IF;

    IF v_total <= 0 THEN
        RAISE EXCEPTION 'Order % has no items', p_order;
    END IF;

    INSERT INTO payments (order_id, method, amount)
    VALUES (p_order, p_method, v_total)
    RETURNING id INTO p_id;

    UPDATE orders
    SET status    = 'PAID',
        closed_at = now()
    WHERE id = p_order;
END
$$;
