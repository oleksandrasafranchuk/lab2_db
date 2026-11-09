package com.example.demo.uow;

import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ReceiptRepository;

public interface UnitOfWork extends AutoCloseable {

    OrderRepository orders();

    ReceiptRepository receipts();

    void commit();

    void rollback();

    @Override
    void close();
}
