package com.example.demo.uow;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqlExecutor {

    private final Connection connection;

    SqlExecutor(Connection connection) {
        this.connection = connection;
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw translate(sql, e);
        }
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public <T> T queryValue(String sql, RowMapper<T> mapper, Object... params) {
        return queryOne(sql, mapper, params)
                .orElseThrow(() -> new DataAccessFailureException("Query returned no rows: " + sql));
    }

    public void call(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.execute();
        } catch (SQLException e) {
            throw translate(sql, e);
        }
    }

    public long callForId(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DataAccessFailureException("Procedure returned no identifier: " + sql);
                }
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            throw translate(sql, e);
        }
    }

    public Array array(String elementType, Object[] values) {
        try {
            return connection.createArrayOf(elementType, values);
        } catch (SQLException e) {
            throw new DataAccessFailureException("Cannot create SQL array of " + elementType, e);
        }
    }

    private void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value == null) {
                statement.setNull(i + 1, Types.OTHER);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    private RuntimeException translate(String sql, SQLException e) {
        String state = e.getSQLState();
        if (state != null && (state.startsWith("23") || state.startsWith("P0"))) {
            return new BusinessRuleException(shortMessage(e), e);
        }
        return new DataAccessFailureException("Database call failed: " + sql, e);
    }

    private String shortMessage(SQLException e) {
        String message = e.getMessage();
        if (message == null) {
            return "Database constraint violated";
        }
        int lineEnd = message.indexOf('\n');
        String firstLine = lineEnd > 0 ? message.substring(0, lineEnd).trim() : message.trim();
        return firstLine.startsWith("ERROR: ") ? firstLine.substring(7).trim() : firstLine;
    }
}
