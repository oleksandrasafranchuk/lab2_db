package com.example.demo.uow;

import com.example.demo.repository.JdbcOrderRepository;
import com.example.demo.repository.JdbcReceiptRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ReceiptRepository;

import java.sql.Connection;
import java.sql.SQLException;

final class JdbcUnitOfWork implements UnitOfWork {

    private final Connection connection;
    private final SqlExecutor sql;

    private OrderRepository orders;
    private ReceiptRepository receipts;

    private boolean completed;

    JdbcUnitOfWork(Connection connection) {
        this.connection = connection;
        this.sql = new SqlExecutor(connection);
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            closeQuietly();
            throw new DataAccessFailureException("Cannot start unit of work", e);
        }
    }

    @Override
    public OrderRepository orders() {
        if (orders == null) {
            orders = new JdbcOrderRepository(sql);
        }
        return orders;
    }

    @Override
    public ReceiptRepository receipts() {
        if (receipts == null) {
            receipts = new JdbcReceiptRepository(sql);
        }
        return receipts;
    }

    @Override
    public void commit() {
        try {
            connection.commit();
            completed = true;
        } catch (SQLException e) {
            throw new DataAccessFailureException("Cannot commit unit of work", e);
        }
    }

    @Override
    public void rollback() {
        try {
            connection.rollback();
            completed = true;
        } catch (SQLException e) {
            throw new DataAccessFailureException("Cannot roll back unit of work", e);
        }
    }

    @Override
    public void close() {
        if (!completed) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                closeQuietly();
                throw new DataAccessFailureException("Cannot roll back unit of work", e);
            }
        }
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessFailureException("Cannot release connection", e);
        }
    }

    private void closeQuietly() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
