-- liquibase formatted sql

--changeset oleksandra:004-view-receipt-lines
CREATE VIEW v_receipt_lines AS
SELECT r.id   AS receipt_id,
       r.receipt_no,
       r.order_id,
       r.channel,
       r.closed_at,
       r.customer_id,
       r.customer_name,
       r.customer_phone,
       r.customer_discount_pct,
       r.waiter_id,
       r.waiter_name,
       r.table_id,
       r.table_number,
       r.hall,
       r.guests,
       r.delivery_address,
       r.courier_name,
       r.eta_minutes,
       r.subtotal,
       r.discount,
       r.total,
       r.payment_method,
       r.paid_at,
       r.rating,
       r.feedback_comment,
       (SELECT coalesce(array_agg(t.tag ORDER BY t.tag), '{}')
        FROM receipt_tags t
        WHERE t.receipt_id = r.id) AS tags,
       i.id   AS item_id,
       i.line_no,
       i.dish_id,
       i.dish_name,
       i.category,
       i.qty,
       i.unit_price,
       i.line_total,
       m.id   AS modifier_id,
       m.name AS modifier_name,
       m.price_delta
FROM receipts r
         LEFT JOIN receipt_items i ON i.receipt_id = r.id
         LEFT JOIN receipt_item_modifiers m ON m.receipt_item_id = i.id;

--changeset oleksandra:004-view-receipts
CREATE VIEW v_receipts AS
SELECT r.id,
       r.receipt_no,
       r.channel,
       r.closed_at,
       r.customer_id,
       r.customer_name,
       r.waiter_name,
       r.hall,
       r.table_number,
       r.total,
       r.payment_method,
       r.rating,
       (SELECT count(*) FROM receipt_items i WHERE i.receipt_id = r.id) AS items_count
FROM receipts r;

--changeset oleksandra:004-sp-receipt-insert splitStatements:false
CREATE PROCEDURE sp_receipt_insert(p_receipt_no TEXT,
                                   p_order_id BIGINT,
                                   p_channel TEXT,
                                   p_closed_at TIMESTAMPTZ,
                                   p_customer_id BIGINT,
                                   p_customer_name TEXT,
                                   p_customer_phone TEXT,
                                   p_customer_discount NUMERIC,
                                   p_waiter_id BIGINT,
                                   p_waiter_name TEXT,
                                   p_table_id INT,
                                   p_table_number INT,
                                   p_hall TEXT,
                                   p_guests INT,
                                   p_delivery_address TEXT,
                                   p_courier_name TEXT,
                                   p_eta_minutes INT,
                                   p_subtotal NUMERIC,
                                   p_discount NUMERIC,
                                   p_total NUMERIC,
                                   p_payment_method TEXT,
                                   p_paid_at TIMESTAMPTZ,
                                   p_rating INT,
                                   p_feedback TEXT,
                                   p_item_dish_ids BIGINT[],
                                   p_item_names TEXT[],
                                   p_item_categories TEXT[],
                                   p_item_qtys INT[],
                                   p_item_prices NUMERIC[],
                                   p_item_line_totals NUMERIC[],
                                   p_mod_line_no INT[],
                                   p_mod_names TEXT[],
                                   p_mod_deltas NUMERIC[],
                                   p_tags TEXT[],
                                   INOUT p_id BIGINT)
    LANGUAGE plpgsql
AS
$$
BEGIN
    INSERT INTO receipts (receipt_no, order_id, channel, closed_at,
                          customer_id, customer_name, customer_phone, customer_discount_pct,
                          waiter_id, waiter_name,
                          table_id, table_number, hall, guests,
                          delivery_address, courier_name, eta_minutes,
                          subtotal, discount, total, payment_method, paid_at,
                          rating, feedback_comment)
    VALUES (p_receipt_no, p_order_id, p_channel, p_closed_at,
            p_customer_id, p_customer_name, p_customer_phone, coalesce(p_customer_discount, 0),
            p_waiter_id, p_waiter_name,
            p_table_id, p_table_number, p_hall, p_guests,
            p_delivery_address, p_courier_name, p_eta_minutes,
            p_subtotal, coalesce(p_discount, 0), p_total, p_payment_method, p_paid_at,
            p_rating, p_feedback)
    RETURNING id INTO p_id;

    INSERT INTO receipt_items (receipt_id, line_no, dish_id, dish_name, category, qty, unit_price, line_total)
    SELECT p_id,
           s.i,
           p_item_dish_ids[s.i],
           p_item_names[s.i],
           p_item_categories[s.i],
           p_item_qtys[s.i],
           p_item_prices[s.i],
           p_item_line_totals[s.i]
    FROM generate_subscripts(p_item_dish_ids, 1) AS s(i);

    IF p_mod_line_no IS NOT NULL AND coalesce(array_length(p_mod_line_no, 1), 0) > 0 THEN
        INSERT INTO receipt_item_modifiers (receipt_item_id, name, price_delta)
        SELECT ri.id, p_mod_names[s.i], p_mod_deltas[s.i]
        FROM generate_subscripts(p_mod_line_no, 1) AS s(i)
                 JOIN receipt_items ri ON ri.receipt_id = p_id AND ri.line_no = p_mod_line_no[s.i];
    END IF;

    IF p_tags IS NOT NULL AND coalesce(array_length(p_tags, 1), 0) > 0 THEN
        INSERT INTO receipt_tags (receipt_id, tag)
        SELECT DISTINCT p_id, t
        FROM unnest(p_tags) AS t;
    END IF;
END
$$;

--changeset oleksandra:004-sp-receipt-rate splitStatements:false
CREATE PROCEDURE sp_receipt_rate(p_receipt_no TEXT, p_rating INT, p_comment TEXT)
    LANGUAGE plpgsql
AS
$$
BEGIN
    UPDATE receipts
    SET rating           = p_rating,
        feedback_comment = p_comment
    WHERE receipt_no = p_receipt_no;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Receipt % not found', p_receipt_no;
    END IF;
END
$$;

--changeset oleksandra:004-sp-receipts-clear splitStatements:false
CREATE PROCEDURE sp_receipts_clear()
    LANGUAGE plpgsql
AS
$$
BEGIN
    TRUNCATE receipt_item_modifiers, receipt_tags, receipt_items, receipts RESTART IDENTITY;
END
$$;

--changeset oleksandra:004-sp-receipts-analyze splitStatements:false
CREATE PROCEDURE sp_receipts_analyze()
    LANGUAGE plpgsql
AS
$$
BEGIN
    ANALYZE receipts;
    ANALYZE receipt_items;
    ANALYZE receipt_item_modifiers;
    ANALYZE receipt_tags;
END
$$;

--changeset oleksandra:004-fn-receipt-top-dishes splitStatements:false
CREATE FUNCTION fn_receipt_top_dishes(p_from TIMESTAMPTZ, p_to TIMESTAMPTZ, p_limit INT)
    RETURNS TABLE
            (
                dish_id  BIGINT,
                dish     TEXT,
                sold_qty BIGINT,
                revenue  NUMERIC
            )
    LANGUAGE sql
    STABLE
AS
$$
SELECT i.dish_id,
       max(i.dish_name)::TEXT AS dish,
       sum(i.qty)::BIGINT     AS sold_qty,
       sum(i.line_total)      AS revenue
FROM receipt_items i
         JOIN receipts r ON r.id = i.receipt_id
WHERE r.closed_at >= p_from
  AND r.closed_at < p_to
GROUP BY i.dish_id
ORDER BY revenue DESC
LIMIT p_limit;
$$;

--changeset oleksandra:004-fn-receipts-by-modifier splitStatements:false
CREATE FUNCTION fn_receipts_by_modifier(p_modifier TEXT, p_limit INT)
    RETURNS TABLE
            (
                receipt_no TEXT,
                closed_at  TIMESTAMPTZ,
                total      NUMERIC,
                dish       TEXT
            )
    LANGUAGE sql
    STABLE
AS
$$
SELECT r.receipt_no::TEXT,
       r.closed_at,
       r.total,
       i.dish_name::TEXT
FROM receipt_item_modifiers m
         JOIN receipt_items i ON i.id = m.receipt_item_id
         JOIN receipts r ON r.id = i.receipt_id
WHERE m.name = p_modifier
ORDER BY r.closed_at DESC
LIMIT p_limit;
$$;

--changeset oleksandra:004-fn-receipt-channel-stats splitStatements:false
CREATE FUNCTION fn_receipt_channel_stats(p_from TIMESTAMPTZ, p_to TIMESTAMPTZ)
    RETURNS TABLE
            (
                channel       TEXT,
                receipts      BIGINT,
                revenue       NUMERIC,
                avg_receipt   NUMERIC,
                avg_positions NUMERIC
            )
    LANGUAGE sql
    STABLE
AS
$$
SELECT r.channel::TEXT,
       count(*)::BIGINT                                        AS receipts,
       sum(r.total)                                            AS revenue,
       round(avg(r.total), 2)                                  AS avg_receipt,
       round(avg((SELECT count(*)
                  FROM receipt_items i
                  WHERE i.receipt_id = r.id)), 2)              AS avg_positions
FROM receipts r
WHERE r.closed_at >= p_from
  AND r.closed_at < p_to
GROUP BY r.channel
ORDER BY revenue DESC;
$$;
