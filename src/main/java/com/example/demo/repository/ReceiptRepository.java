package com.example.demo.repository;

import com.example.demo.model.ChannelStatsView;
import com.example.demo.model.Receipt;
import com.example.demo.model.ReceiptRef;
import com.example.demo.model.TopDishView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository {

    long insert(Receipt receipt);

    Optional<Receipt> findByNo(String receiptNo);

    List<Receipt> findByCustomerAndPeriod(long customerId, OffsetDateTime from, OffsetDateTime to);

    List<TopDishView> topDishes(OffsetDateTime from, OffsetDateTime to, int limit);

    List<ReceiptRef> findByModifier(String modifier, int limit);

    List<ChannelStatsView> channelStats(OffsetDateTime from, OffsetDateTime to);

    void rate(String receiptNo, int rating, String comment);

    long count();

    void clear();

    void analyze();
}
