package com.example.demo.repository;

import com.example.demo.model.ChannelStatsView;
import com.example.demo.model.OrderItemView;
import com.example.demo.model.OrderView;
import com.example.demo.model.PaymentView;
import com.example.demo.model.ReceiptRef;
import com.example.demo.model.TopDishView;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

final class RowMappers {

    private RowMappers() {
    }

    static OrderView order(ResultSet rs) throws SQLException {
        return new OrderView(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getBigDecimal("total"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class),
                rs.getInt("table_id"),
                rs.getInt("table_number"),
                rs.getString("hall"),
                rs.getLong("waiter_id"),
                rs.getString("waiter"),
                rs.getObject("customer_id", Long.class),
                rs.getString("customer"),
                rs.getString("customer_phone"),
                rs.getBigDecimal("customer_discount_pct"),
                rs.getInt("items_count"));
    }

    static OrderItemView orderItem(ResultSet rs) throws SQLException {
        return new OrderItemView(
                rs.getLong("item_id"),
                rs.getLong("order_id"),
                rs.getLong("dish_id"),
                rs.getString("dish"),
                rs.getString("category"),
                rs.getInt("qty"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("line_total"));
    }

    static PaymentView payment(ResultSet rs) throws SQLException {
        return new PaymentView(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getString("method"),
                rs.getBigDecimal("amount"),
                rs.getObject("paid_at", OffsetDateTime.class));
    }

    static TopDishView topDish(ResultSet rs) throws SQLException {
        return new TopDishView(
                rs.getLong("dish_id"),
                rs.getString("dish"),
                rs.getLong("sold_qty"),
                rs.getBigDecimal("revenue"));
    }

    static ReceiptRef receiptRef(ResultSet rs) throws SQLException {
        return new ReceiptRef(
                rs.getString("receipt_no"),
                rs.getObject("closed_at", OffsetDateTime.class),
                rs.getBigDecimal("total"),
                rs.getString("dish"));
    }

    static ChannelStatsView channelStats(ResultSet rs) throws SQLException {
        return new ChannelStatsView(
                rs.getString("channel"),
                rs.getLong("receipts"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("avg_receipt"),
                rs.getBigDecimal("avg_positions"));
    }
}
