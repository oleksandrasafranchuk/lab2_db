package com.example.demo.nosql.mongo;

import com.example.demo.model.ChannelStatsView;
import com.example.demo.model.Receipt;
import com.example.demo.model.ReceiptItem;
import com.example.demo.model.ReceiptModifier;
import com.example.demo.model.ReceiptRef;
import com.example.demo.model.TopDishView;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class MongoReceiptRepository {

    private final MongoTemplate mongo;
    private final String collectionName;

    public MongoReceiptRepository(MongoTemplate mongo,
                                  @Value("${restaurant.nosql.receipts-collection}") String collectionName) {
        this.mongo = mongo;
        this.collectionName = collectionName;
    }

    public void ensureIndexes() {
        MongoCollection<Document> receipts = collection();
        receipts.createIndex(Indexes.ascending("receiptNo"), new IndexOptions().unique(true));
        receipts.createIndex(Indexes.compoundIndex(Indexes.ascending("customer.id"), Indexes.descending("closedAt")));
        receipts.createIndex(Indexes.descending("closedAt"));
        receipts.createIndex(Indexes.ascending("channel"));
        receipts.createIndex(Indexes.ascending("items.dishId"));
        receipts.createIndex(Indexes.ascending("items.modifiers.name"));
        receipts.createIndex(Indexes.ascending("tags"));
    }

    public String insert(Receipt receipt) {
        Document document = ReceiptDocuments.toDocument(receipt);
        durableCollection().insertOne(document);
        return document.getObjectId("_id").toHexString();
    }

    public void insertMany(List<Receipt> receipts) {
        List<Document> documents = new ArrayList<>(receipts.size());
        for (Receipt receipt : receipts) {
            documents.add(ReceiptDocuments.toDocument(receipt));
        }
        durableCollection().insertMany(documents);
    }

    public Optional<Receipt> findByNo(String receiptNo) {
        Document document = collection().find(Filters.eq("receiptNo", receiptNo)).first();
        return Optional.ofNullable(document).map(ReceiptDocuments::toReceipt);
    }

    public List<Receipt> findByCustomerAndPeriod(long customerId, OffsetDateTime from, OffsetDateTime to) {
        Bson filter = Filters.and(
                Filters.eq("customer.id", customerId),
                Filters.gte("closedAt", date(from)),
                Filters.lt("closedAt", date(to)));

        List<Receipt> receipts = new ArrayList<>();
        for (Document document : collection().find(filter).sort(Sorts.descending("closedAt"))) {
            receipts.add(ReceiptDocuments.toReceipt(document));
        }
        return receipts;
    }

    public List<TopDishView> topDishes(OffsetDateTime from, OffsetDateTime to, int limit) {
        List<Bson> pipeline = List.of(
                Aggregates.match(Filters.and(
                        Filters.gte("closedAt", date(from)),
                        Filters.lt("closedAt", date(to)))),
                Aggregates.unwind("$items"),
                Aggregates.group("$items.dishId",
                        Accumulators.first("dish", "$items.dish"),
                        Accumulators.sum("soldQty", "$items.qty"),
                        Accumulators.sum("revenue", "$items.lineTotal")),
                Aggregates.sort(Sorts.descending("revenue")),
                Aggregates.limit(limit));

        List<TopDishView> rows = new ArrayList<>();
        for (Document document : collection().aggregate(pipeline)) {
            rows.add(new TopDishView(
                    document.get("_id", Number.class).longValue(),
                    document.getString("dish"),
                    document.get("soldQty", Number.class).longValue(),
                    decimal(document.get("revenue"))));
        }
        return rows;
    }

    public List<ReceiptRef> findByModifier(String modifier, int limit) {
        List<ReceiptRef> rows = new ArrayList<>();
        var cursor = collection()
                .find(Filters.eq("items.modifiers.name", modifier))
                .sort(Sorts.descending("closedAt"))
                .limit(limit);
        for (Document document : cursor) {
            Receipt receipt = ReceiptDocuments.toReceipt(document);
            rows.add(new ReceiptRef(
                    receipt.receiptNo(),
                    receipt.closedAt(),
                    receipt.total(),
                    dishWithModifier(receipt, modifier)));
        }
        return rows;
    }

    public List<ChannelStatsView> channelStats(OffsetDateTime from, OffsetDateTime to) {
        List<Bson> pipeline = List.of(
                Aggregates.match(Filters.and(
                        Filters.gte("closedAt", date(from)),
                        Filters.lt("closedAt", date(to)))),
                Aggregates.group("$channel",
                        Accumulators.sum("receipts", 1),
                        Accumulators.sum("revenue", "$total"),
                        Accumulators.avg("avgReceipt", "$total"),
                        Accumulators.avg("avgPositions", new Document("$size", "$items"))),
                Aggregates.sort(Sorts.descending("revenue")));

        List<ChannelStatsView> rows = new ArrayList<>();
        for (Document document : collection().aggregate(pipeline)) {
            rows.add(new ChannelStatsView(
                    document.getString("_id"),
                    document.get("receipts", Number.class).longValue(),
                    decimal(document.get("revenue")),
                    round(decimal(document.get("avgReceipt"))),
                    round(decimal(document.get("avgPositions")))));
        }
        return rows;
    }

    public void rate(String receiptNo, int rating, String comment) {
        durableCollection().updateOne(
                Filters.eq("receiptNo", receiptNo),
                Updates.combine(
                        Updates.set("feedback.rating", rating),
                        Updates.set("feedback.comment", comment)));
    }

    public long count() {
        return collection().countDocuments();
    }

    public void clear() {
        collection().deleteMany(new Document());
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection(collectionName);
    }

    private MongoCollection<Document> durableCollection() {
        return collection().withWriteConcern(WriteConcern.JOURNALED);
    }

    private static String dishWithModifier(Receipt receipt, String modifier) {
        for (ReceiptItem item : receipt.items()) {
            for (ReceiptModifier candidate : item.modifiers()) {
                if (modifier.equals(candidate.name())) {
                    return item.dish();
                }
            }
        }
        return null;
    }

    private static Date date(OffsetDateTime value) {
        return Date.from(value.toInstant());
    }

    private static BigDecimal decimal(Object value) {
        return switch (value) {
            case null -> null;
            case Decimal128 decimal -> decimal.bigDecimalValue();
            case Number number -> BigDecimal.valueOf(number.doubleValue());
            default -> new BigDecimal(value.toString());
        };
    }

    private static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
