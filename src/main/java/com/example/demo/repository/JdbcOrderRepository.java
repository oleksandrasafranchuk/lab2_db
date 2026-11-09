package com.example.demo.repository;

import com.example.demo.model.OrderItemView;
import com.example.demo.model.OrderView;
import com.example.demo.model.PaymentView;
import com.example.demo.uow.SqlExecutor;

import java.util.List;
import java.util.Optional;

public final class JdbcOrderRepository implements OrderRepository {

    private static final String SELECT_ORDERS = """
            SELECT id, status, total, created_at, closed_at,
                   table_id, table_number, hall, waiter_id, waiter,
                   customer_id, customer, customer_phone, customer_discount_pct, items_count
            FROM v_orders
            """;

    private final SqlExecutor sql;

    public JdbcOrderRepository(SqlExecutor sql) {
        this.sql = sql;
    }

    @Override
    public Optional<OrderView> findById(long id) {
        return sql.queryOne(SELECT_ORDERS + "WHERE id = CAST(? AS BIGINT)", RowMappers::order, id);
    }

    @Override
    public List<OrderItemView> findItems(long orderId) {
        return sql.query(
                """
                        SELECT item_id, order_id, dish_id, dish, category, qty, unit_price, line_total
                        FROM v_order_details
                        WHERE order_id = CAST(? AS BIGINT)
                        ORDER BY item_id
                        """,
                RowMappers::orderItem,
                orderId);
    }

    @Override
    public List<PaymentView> findPayments(long orderId) {
        return sql.query(
                """
                        SELECT id, order_id, method, amount, paid_at
                        FROM v_order_payments
                        WHERE order_id = CAST(? AS BIGINT)
                        ORDER BY paid_at
                        """,
                RowMappers::payment,
                orderId);
    }

    @Override
    public long create(int tableId, long waiterId, Long customerId) {
        return sql.callForId(
                "CALL sp_order_create(CAST(? AS INT), CAST(? AS BIGINT), CAST(? AS BIGINT), NULL)",
                tableId, waiterId, customerId);
    }

    @Override
    public long addItem(long orderId, long dishId, int qty) {
        return sql.callForId(
                "CALL sp_order_add_item(CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS INT), NULL)",
                orderId, dishId, qty);
    }

    @Override
    public long pay(long orderId, String method) {
        return sql.callForId(
                "CALL sp_order_pay(CAST(? AS BIGINT), CAST(? AS TEXT), NULL)",
                orderId, method);
    }
}
