package com.example.demo.uow;

public class DataAccessFailureException extends RuntimeException {

    public DataAccessFailureException(String message) {
        super(message);
    }

    public DataAccessFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
