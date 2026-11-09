package com.example.demo.nosql.bench;

import com.example.demo.model.Receipt;
import com.example.demo.model.ReceiptItem;
import com.example.demo.model.ReceiptModifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Component
public class ReceiptGenerator {

    public static final String NUMBER_FORMAT = "B-%07d";
    public static final String PROBE_MODIFIER = "+подвійна порція";
    public static final String PROBE_TAG = "birthday";
    public static final long PROBE_CUSTOMER_ID = 3;

    private static final Dish[] MENU = {
            new Dish(1, "Борщ український", "Супи", "145.00"),
            new Dish(2, "Крем-суп грибний", "Супи", "135.00"),
            new Dish(3, "Котлета по-київськи", "Основні страви", "265.00"),
            new Dish(4, "Стейк Рібай", "Основні страви", "690.00"),
            new Dish(5, "Вареники з картоплею", "Основні страви", "155.00"),
            new Dish(6, "Салат Цезар", "Салати", "210.00"),
            new Dish(7, "Салат Грецький", "Салати", "185.00"),
            new Dish(8, "Americano", "Напої", "55.00"),
            new Dish(9, "Лимонад домашній", "Напої", "85.00"),
            new Dish(10, "Чізкейк Нью-Йорк", "Десерти", "125.00")
    };

    private static final Staff[] WAITERS = {
            new Staff(3, "Марія Коваль"),
            new Staff(4, "Андрій Шевчук"),
            new Staff(2, "Ігор Лисенко")
    };

    private static final Guest[] GUESTS = {
            new Guest(1, "Петро Іваненко", "+380501110011", "0"),
            new Guest(2, "Оксана Гриценко", "+380501110012", "5"),
            new Guest(3, "Дмитро Савченко", "+380501110013", "10"),
            new Guest(4, "Юлія Романюк", "+380501110014", "15"),
            new Guest(5, "Назар Поліщук", "+380501110015", "0")
    };

    private static final String[] HALLS = {"Головний зал", "Літня тераса", "VIP кабінет"};

    private static final String[] COURIERS = {"Олег Бондар", "Ірина Савчук", "Максим Деркач"};

    private static final String[] ADDRESSES = {
            "вул. Хрещатик, 12, кв. 5",
            "просп. Перемоги, 48, кв. 71",
            "вул. Сагайдачного, 3, офіс 2",
            "вул. Лесі Українки, 26, кв. 140"
    };

    private static final String[] TAGS = {
            PROBE_TAG, "promo-10", "business-lunch", "loyalty", "corporate", "delivery-box"
    };

    private static final Modifier[] SOUP_MODS = {
            new Modifier("без сметани", "0.00"),
            new Modifier("гостро", "0.00")
    };

    private static final Modifier[] MAIN_MODS = {
            new Modifier("ступінь прожарки: medium", "0.00"),
            new Modifier("без гарніру", "0.00"),
            new Modifier(PROBE_MODIFIER, "120.00"),
            new Modifier("соус окремо", "0.00")
    };

    private static final Modifier[] SALAD_MODS = {
            new Modifier("без сухариків", "0.00"),
            new Modifier("заправка окремо", "0.00"),
            new Modifier("+подвійна курка", "45.00")
    };

    private static final Modifier[] DRINK_MODS = {
            new Modifier("без цукру", "0.00"),
            new Modifier("з льодом", "0.00"),
            new Modifier("+сироп карамель", "15.00")
    };

    private static final Modifier[] DESSERT_MODS = {
            new Modifier("+кулька морозива", "35.00")
    };

    public List<Receipt> generate(int count, long seed, int startFrom) {
        Random random = new Random(seed);
        OffsetDateTime base = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        List<Receipt> receipts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            receipts.add(one(startFrom + i, base, random));
        }
        return receipts;
    }

    public Receipt one(int number, OffsetDateTime base, Random random) {
        String channel = channel(random);
        Guest guest = GUESTS[random.nextInt(GUESTS.length)];
        Staff waiter = WAITERS[random.nextInt(WAITERS.length)];
        OffsetDateTime closedAt = base
                .plusDays(random.nextInt(180))
                .plusMinutes(random.nextInt(600));

        int positions = 1 + random.nextInt(5);
        List<ReceiptItem> items = new ArrayList<>(positions);
        BigDecimal subtotal = BigDecimal.ZERO;

        for (int lineNo = 1; lineNo <= positions; lineNo++) {
            Dish dish = MENU[random.nextInt(MENU.length)];
            int qty = 1 + random.nextInt(3);
            List<ReceiptModifier> modifiers = modifiers(dish.category(), random);

            BigDecimal deltas = BigDecimal.ZERO;
            for (ReceiptModifier modifier : modifiers) {
                deltas = deltas.add(modifier.priceDelta());
            }
            BigDecimal unitPrice = new BigDecimal(dish.price());
            BigDecimal lineTotal = unitPrice.add(deltas)
                    .multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            items.add(new ReceiptItem(lineNo, dish.id(), dish.name(), dish.category(),
                    qty, unitPrice, lineTotal, modifiers));
        }

        BigDecimal discountPct = new BigDecimal(guest.discountPct());
        BigDecimal discount = subtotal
                .multiply(discountPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);

        boolean delivery = "DELIVERY".equals(channel);
        Integer rating = random.nextInt(100) < 35 ? 1 + random.nextInt(5) : null;

        return new Receipt(
                NUMBER_FORMAT.formatted(number),
                (long) number,
                channel,
                closedAt,
                guest.id(),
                guest.name(),
                guest.phone(),
                discountPct,
                waiter.id(),
                waiter.name(),
                delivery ? null : 1 + random.nextInt(7),
                delivery ? null : 1 + random.nextInt(4),
                delivery ? null : HALLS[random.nextInt(HALLS.length)],
                delivery ? null : 1 + random.nextInt(6),
                delivery ? ADDRESSES[random.nextInt(ADDRESSES.length)] : null,
                delivery ? COURIERS[random.nextInt(COURIERS.length)] : null,
                delivery ? 25 + random.nextInt(45) : null,
                subtotal.setScale(2, RoundingMode.HALF_UP),
                discount,
                total,
                random.nextBoolean() ? "CARD" : "CASH",
                closedAt,
                rating,
                rating == null ? null : "Відгук до чека " + NUMBER_FORMAT.formatted(number),
                items,
                tags(random));
    }

    private static String channel(Random random) {
        int roll = random.nextInt(100);
        if (roll < 70) {
            return "DINE_IN";
        }
        return roll < 90 ? "DELIVERY" : "TAKEAWAY";
    }

    private static List<ReceiptModifier> modifiers(String category, Random random) {
        Modifier[] pool = switch (category) {
            case "Супи" -> SOUP_MODS;
            case "Основні страви" -> MAIN_MODS;
            case "Салати" -> SALAD_MODS;
            case "Напої" -> DRINK_MODS;
            default -> DESSERT_MODS;
        };
        int count = random.nextInt(100) < 45 ? 1 + random.nextInt(2) : 0;
        if (count == 0) {
            return List.of();
        }
        Set<String> used = new LinkedHashSet<>();
        List<ReceiptModifier> modifiers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Modifier modifier = pool[random.nextInt(pool.length)];
            if (used.add(modifier.name())) {
                modifiers.add(new ReceiptModifier(modifier.name(), new BigDecimal(modifier.priceDelta())));
            }
        }
        return modifiers;
    }

    private static List<String> tags(Random random) {
        int count = random.nextInt(100) < 40 ? 1 + random.nextInt(2) : 0;
        if (count == 0) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            tags.add(TAGS[random.nextInt(TAGS.length)]);
        }
        return List.copyOf(tags);
    }

    private record Dish(long id, String name, String category, String price) {
    }

    private record Staff(long id, String name) {
    }

    private record Guest(long id, String name, String phone, String discountPct) {
    }

    private record Modifier(String name, String priceDelta) {
    }
}
