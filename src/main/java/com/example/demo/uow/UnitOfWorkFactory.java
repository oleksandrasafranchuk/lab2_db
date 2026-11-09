package com.example.demo.uow;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class UnitOfWorkFactory {

    private final DataSource dataSource;

    public UnitOfWorkFactory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UnitOfWork open() {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new DataAccessFailureException("Cannot obtain connection", e);
        }
        return new JdbcUnitOfWork(connection);
    }

    public <T> T execute(Function<UnitOfWork, T> work) {
        try (UnitOfWork unitOfWork = open()) {
            T result = work.apply(unitOfWork);
            unitOfWork.commit();
            return result;
        }
    }

    public void executeVoid(Consumer<UnitOfWork> work) {
        execute(unitOfWork -> {
            work.accept(unitOfWork);
            return null;
        });
    }
}
