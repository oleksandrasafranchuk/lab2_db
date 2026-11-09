package com.example.demo.nosql.bench;

import com.example.demo.model.Receipt;
import com.example.demo.nosql.mongo.MongoReceiptRepository;
import com.example.demo.repository.ReceiptRepository;
import com.example.demo.uow.UnitOfWork;
import com.example.demo.uow.UnitOfWorkFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class BenchmarkService {

    private static final OffsetDateTime PERIOD_FROM =
            OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime PERIOD_TO = PERIOD_FROM.plusDays(181);
    private static final long DATA_SEED = 20_260_105L;
    private static final int TOP_DISHES_LIMIT = 5;
    private static final int MODIFIER_SEARCH_LIMIT = 50;
    private static final int LOAD_COMMIT_CHUNK = 500;
    private static final int MONGO_INSERT_CHUNK = 1000;

    private final UnitOfWorkFactory factory;
    private final MongoReceiptRepository mongo;
    private final ReceiptGenerator generator;
    private final int defaultReceipts;
    private final int defaultIterations;
    private final int defaultWarmup;

    public BenchmarkService(UnitOfWorkFactory factory,
                            MongoReceiptRepository mongo,
                            ReceiptGenerator generator,
                            @Value("${restaurant.benchmark.receipts}") int defaultReceipts,
                            @Value("${restaurant.benchmark.iterations}") int defaultIterations,
                            @Value("${restaurant.benchmark.warmup}") int defaultWarmup) {
        this.factory = factory;
        this.mongo = mongo;
        this.generator = generator;
        this.defaultReceipts = defaultReceipts;
        this.defaultIterations = defaultIterations;
        this.defaultWarmup = defaultWarmup;
    }

    public BenchmarkReport run(Integer receiptsParam, Integer iterationsParam, Integer warmupParam) {
        int receipts = receiptsParam == null ? defaultReceipts : receiptsParam;
        int iterations = iterationsParam == null ? defaultIterations : iterationsParam;
        int warmup = warmupParam == null ? defaultWarmup : warmupParam;

        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        long preparationStart = System.nanoTime();

        List<Receipt> dataset = generator.generate(receipts, DATA_SEED, 1);
        List<Receipt> freshReceipts = generator.generate(warmup + iterations, DATA_SEED + 1, receipts + 1);

        reset();
        loadSql(dataset);
        loadMongo(dataset);

        long preparationMs = (System.nanoTime() - preparationStart) / 1_000_000;

        String[] keys = probeKeys(receipts, warmup + iterations);

        List<ScenarioComparison> scenarios = new ArrayList<>();
        scenarios.add(readReceipt(warmup, iterations, keys));
        scenarios.add(customerHistory(warmup, iterations));
        scenarios.add(topDishes(warmup, iterations));
        scenarios.add(modifierSearch(warmup, iterations));
        scenarios.add(channelStats(warmup, iterations));
        scenarios.add(rateReceipt(warmup, iterations, keys));
        scenarios.add(writeReceipt(warmup, iterations, freshReceipts));

        return new BenchmarkReport(startedAt, receipts, iterations, warmup, preparationMs, scenarios);
    }

    public void reset() {
        factory.executeVoid(unitOfWork -> unitOfWork.receipts().clear());
        mongo.clear();
        mongo.ensureIndexes();
    }

    public long loadSql(List<Receipt> receipts) {
        try (UnitOfWork unitOfWork = factory.open()) {
            ReceiptRepository repository = unitOfWork.receipts();
            int pending = 0;
            for (Receipt receipt : receipts) {
                repository.insert(receipt);
                if (++pending == LOAD_COMMIT_CHUNK) {
                    unitOfWork.commit();
                    pending = 0;
                }
            }
            unitOfWork.commit();
            repository.analyze();
            unitOfWork.commit();
            return repository.count();
        }
    }

    public long loadMongo(List<Receipt> receipts) {
        for (int from = 0; from < receipts.size(); from += MONGO_INSERT_CHUNK) {
            int to = Math.min(from + MONGO_INSERT_CHUNK, receipts.size());
            mongo.insertMany(receipts.subList(from, to));
        }
        return mongo.count();
    }

    private ScenarioComparison readReceipt(int warmup, int iterations, String[] keys) {
        Counter sqlKey = new Counter();
        Counter mongoKey = new Counter();

        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.findByNo(keys[sqlKey.next(keys.length)])
                        .map(receipt -> (long) receipt.items().size())
                        .orElse(0L));

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.findByNo(keys[mongoKey.next(keys.length)])
                        .map(receipt -> (long) receipt.items().size())
                        .orElse(0L));

        return compare("read-receipt",
                "Повний чек за номером: шапка, позиції, модифікатори, теги",
                "SELECT з v_receipt_lines — receipts ⋈ receipt_items ⋈ receipt_item_modifiers + array_agg тегів, збирання агрегату в коді",
                "find({receiptNo}) — документ повертається цілком, збирання не потрібне",
                postgres, mongodb);
    }

    private ScenarioComparison customerHistory(int warmup, int iterations) {
        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.findByCustomerAndPeriod(ReceiptGenerator.PROBE_CUSTOMER_ID, PERIOD_FROM, PERIOD_TO).size());

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.findByCustomerAndPeriod(ReceiptGenerator.PROBE_CUSTOMER_ID, PERIOD_FROM, PERIOD_TO).size());

        return compare("customer-history",
                "Історія чеків одного клієнта за півроку з позиціями",
                "індекс (customer_id, closed_at DESC) + два з'єднання на кожен чек",
                "індекс {customer.id, closedAt} + вибірка готових документів",
                postgres, mongodb);
    }

    private ScenarioComparison topDishes(int warmup, int iterations) {
        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.topDishes(PERIOD_FROM, PERIOD_TO, TOP_DISHES_LIMIT).size());

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.topDishes(PERIOD_FROM, PERIOD_TO, TOP_DISHES_LIMIT).size());

        return compare("top-dishes",
                "Топ-5 страв за виручкою за період",
                "fn_receipt_top_dishes — JOIN receipt_items з receipts, GROUP BY dish_id",
                "aggregate: $match → $unwind items → $group → $sort → $limit",
                postgres, mongodb);
    }

    private ScenarioComparison modifierSearch(int warmup, int iterations) {
        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.findByModifier(ReceiptGenerator.PROBE_MODIFIER, MODIFIER_SEARCH_LIMIT).size());

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.findByModifier(ReceiptGenerator.PROBE_MODIFIER, MODIFIER_SEARCH_LIMIT).size());

        return compare("modifier-search",
                "Чеки, де є позиція з модифікатором «" + ReceiptGenerator.PROBE_MODIFIER + "»",
                "fn_receipts_by_modifier — три з'єднання: modifiers → items → receipts",
                "find({'items.modifiers.name': ...}) по багатоключовому індексу",
                postgres, mongodb);
    }

    private ScenarioComparison channelStats(int warmup, int iterations) {
        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.channelStats(PERIOD_FROM, PERIOD_TO).size());

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.channelStats(PERIOD_FROM, PERIOD_TO).size());

        return compare("channel-stats",
                "Зріз по каналах продажу: виручка, середній чек, середня кількість позицій",
                "fn_receipt_channel_stats — GROUP BY channel з підзапитом на кількість позицій",
                "aggregate: $group з $avg над $size масиву items",
                postgres, mongodb);
    }

    private ScenarioComparison rateReceipt(int warmup, int iterations, String[] keys) {
        Counter sqlKey = new Counter();
        Counter mongoKey = new Counter();

        Measurement postgres;
        try (UnitOfWork unitOfWork = factory.open()) {
            ReceiptRepository repository = unitOfWork.receipts();
            postgres = measure(warmup, iterations, () -> {
                int index = sqlKey.next(keys.length);
                repository.rate(keys[index], 1 + index % 5, "Оцінка " + keys[index]);
                unitOfWork.commit();
                return 1L;
            });
        }

        Measurement mongodb = measure(warmup, iterations, () -> {
            int index = mongoKey.next(keys.length);
            mongo.rate(keys[index], 1 + index % 5, "Оцінка " + keys[index]);
            return 1L;
        });

        return compare("rate-receipt",
                "Проставлення оцінки вже закритому чеку",
                "CALL sp_receipt_rate(...) — UPDATE одного рядка receipts за унікальним індексом",
                "updateOne({receiptNo}, $set) — зміна двох полів усередині документа",
                postgres, mongodb);
    }

    private ScenarioComparison writeReceipt(int warmup, int iterations, List<Receipt> fresh) {
        Counter sqlIndex = new Counter();
        Counter mongoIndex = new Counter();

        Measurement postgres;
        try (UnitOfWork unitOfWork = factory.open()) {
            ReceiptRepository repository = unitOfWork.receipts();
            postgres = measure(warmup, iterations, () -> {
                Receipt receipt = fresh.get(sqlIndex.next(fresh.size()));
                repository.insert(receipt);
                unitOfWork.commit();
                return (long) receipt.items().size();
            });
        }

        Measurement mongodb = measure(warmup, iterations, () -> {
            Receipt receipt = fresh.get(mongoIndex.next(fresh.size()));
            mongo.insert(receipt);
            return (long) receipt.items().size();
        });

        return compare("write-receipt",
                "Запис одного закритого чека з позиціями та модифікаторами",
                "CALL sp_receipt_insert(...) — вставка у 4 таблиці однією транзакцією, один round-trip",
                "insertOne(document) — один документ, один round-trip",
                postgres, mongodb);
    }

    private Measurement measureSql(int warmup, int iterations, SqlOperation operation) {
        try (UnitOfWork unitOfWork = factory.open()) {
            ReceiptRepository repository = unitOfWork.receipts();
            Measurement measurement = measure(warmup, iterations, () -> operation.execute(repository));
            unitOfWork.commit();
            return measurement;
        }
    }

    private Measurement measure(int warmup, int iterations, Operation operation) {
        for (int i = 0; i < warmup; i++) {
            operation.execute();
        }
        long[] nanos = new long[iterations];
        long rows = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            rows = operation.execute();
            nanos[i] = System.nanoTime() - start;
        }
        return Measurement.of(nanos, rows);
    }

    private static ScenarioComparison compare(String scenario,
                                              String description,
                                              String sqlPlan,
                                              String mongoPlan,
                                              Measurement postgres,
                                              Measurement mongodb) {
        double speedup = mongodb.avgMs() == 0 ? 0 : postgres.avgMs() / mongodb.avgMs();
        speedup = Math.round(speedup * 100.0) / 100.0;
        String faster = postgres.avgMs() <= mongodb.avgMs() ? "postgres" : "mongodb";
        return new ScenarioComparison(scenario, description, sqlPlan, mongoPlan, postgres, mongodb, speedup, faster);
    }

    private static String[] probeKeys(int receipts, int count) {
        Random random = new Random(DATA_SEED);
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = ReceiptGenerator.NUMBER_FORMAT.formatted(1 + random.nextInt(receipts));
        }
        return keys;
    }

    @FunctionalInterface
    private interface Operation {

        long execute();
    }

    @FunctionalInterface
    private interface SqlOperation {

        long execute(ReceiptRepository repository);
    }

    private static final class Counter {

        private int value;

        private int next(int bound) {
            int current = value;
            value = (value + 1) % bound;
            return current;
        }
    }
}
