package com.example.demo.nosql.mongo;

import com.example.demo.model.Receipt;
import com.example.demo.model.ReceiptItem;
import com.example.demo.model.ReceiptModifier;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ReceiptDocuments {

    private ReceiptDocuments() {
    }

    public static Document toDocument(Receipt receipt) {
        Document document = new Document()
                .append("receiptNo", receipt.receiptNo())
                .append("orderId", receipt.orderId())
                .append("channel", receipt.channel())
                .append("closedAt", date(receipt.closedAt()))
                .append("customer", new Document()
                        .append("id", receipt.customerId())
                        .append("name", receipt.customerName())
                        .append("phone", receipt.customerPhone())
                        .append("discountPct", decimal(receipt.customerDiscountPct())))
                .append("waiter", new Document()
                        .append("id", receipt.waiterId())
                        .append("name", receipt.waiterName()))
                .append("items", items(receipt.items()))
                .append("subtotal", decimal(receipt.subtotal()))
                .append("discount", decimal(receipt.discount()))
                .append("total", decimal(receipt.total()))
                .append("payment", new Document()
                        .append("method", receipt.paymentMethod())
                        .append("paidAt", date(receipt.paidAt())))
                .append("tags", receipt.tags());

        if ("DELIVERY".equals(receipt.channel())) {
            document.append("delivery", new Document()
                    .append("address", receipt.deliveryAddress())
                    .append("courier", receipt.courierName())
                    .append("etaMinutes", receipt.etaMinutes()));
        } else {
            document.append("place", new Document()
                    .append("tableId", receipt.tableId())
                    .append("tableNumber", receipt.tableNumber())
                    .append("hall", receipt.hall())
                    .append("guests", receipt.guests()));
        }

        if (receipt.rating() != null) {
            document.append("feedback", new Document()
                    .append("rating", receipt.rating())
                    .append("comment", receipt.feedbackComment()));
        }
        return document;
    }

    public static Receipt toReceipt(Document document) {
        Document customer = sub(document, "customer");
        Document waiter = sub(document, "waiter");
        Document place = sub(document, "place");
        Document delivery = sub(document, "delivery");
        Document payment = sub(document, "payment");
        Document feedback = sub(document, "feedback");

        return new Receipt(
                document.getString("receiptNo"),
                number(document.get("orderId")),
                document.getString("channel"),
                offsetDateTime(document.getDate("closedAt")),
                number(customer.get("id")),
                customer.getString("name"),
                customer.getString("phone"),
                bigDecimal(customer.get("discountPct")),
                number(waiter.get("id")),
                waiter.getString("name"),
                place.getInteger("tableId"),
                place.getInteger("tableNumber"),
                place.getString("hall"),
                place.getInteger("guests"),
                delivery.getString("address"),
                delivery.getString("courier"),
                delivery.getInteger("etaMinutes"),
                bigDecimal(document.get("subtotal")),
                bigDecimal(document.get("discount")),
                bigDecimal(document.get("total")),
                payment.getString("method"),
                offsetDateTime(payment.getDate("paidAt")),
                feedback.getInteger("rating"),
                feedback.getString("comment"),
                items(document),
                tags(document));
    }

    private static List<Document> items(List<ReceiptItem> items) {
        List<Document> documents = new ArrayList<>(items.size());
        for (ReceiptItem item : items) {
            List<Document> modifiers = new ArrayList<>(item.modifiers().size());
            for (ReceiptModifier modifier : item.modifiers()) {
                modifiers.add(new Document()
                        .append("name", modifier.name())
                        .append("priceDelta", decimal(modifier.priceDelta())));
            }
            documents.add(new Document()
                    .append("lineNo", item.lineNo())
                    .append("dishId", item.dishId())
                    .append("dish", item.dish())
                    .append("category", item.category())
                    .append("qty", item.qty())
                    .append("unitPrice", decimal(item.unitPrice()))
                    .append("lineTotal", decimal(item.lineTotal()))
                    .append("modifiers", modifiers));
        }
        return documents;
    }

    @SuppressWarnings("unchecked")
    private static List<ReceiptItem> items(Document document) {
        List<Document> raw = (List<Document>) document.get("items");
        if (raw == null) {
            return List.of();
        }
        List<ReceiptItem> items = new ArrayList<>(raw.size());
        for (Document item : raw) {
            List<Document> rawModifiers = (List<Document>) item.get("modifiers");
            List<ReceiptModifier> modifiers = new ArrayList<>();
            if (rawModifiers != null) {
                for (Document modifier : rawModifiers) {
                    modifiers.add(new ReceiptModifier(
                            modifier.getString("name"),
                            bigDecimal(modifier.get("priceDelta"))));
                }
            }
            items.add(new ReceiptItem(
                    item.getInteger("lineNo", 0),
                    item.get("dishId", Number.class).longValue(),
                    item.getString("dish"),
                    item.getString("category"),
                    item.getInteger("qty", 0),
                    bigDecimal(item.get("unitPrice")),
                    bigDecimal(item.get("lineTotal")),
                    List.copyOf(modifiers)));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private static List<String> tags(Document document) {
        List<String> tags = (List<String>) document.get("tags");
        return tags == null ? List.of() : List.copyOf(tags);
    }

    private static Document sub(Document document, String key) {
        Document nested = document.get(key, Document.class);
        return nested == null ? new Document() : nested;
    }

    private static Decimal128 decimal(BigDecimal value) {
        return value == null ? null : new Decimal128(value);
    }

    private static BigDecimal bigDecimal(Object value) {
        return switch (value) {
            case null -> null;
            case Decimal128 decimal -> decimal.bigDecimalValue();
            case Number number -> BigDecimal.valueOf(number.doubleValue());
            default -> new BigDecimal(value.toString());
        };
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Date date(OffsetDateTime value) {
        return value == null ? null : Date.from(value.toInstant());
    }

    private static OffsetDateTime offsetDateTime(Date value) {
        if (value == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(value.getTime()), ZoneOffset.UTC);
    }
}
