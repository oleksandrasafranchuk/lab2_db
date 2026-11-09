package com.example.demo.nosql.redis;

import com.example.demo.model.Receipt;
import com.example.demo.model.ReceiptItem;
import com.example.demo.uow.DataAccessFailureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisReceiptBoard {

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String prefix;
    private final Duration defaultTtl;

    public RedisReceiptBoard(StringRedisTemplate redis,
                             ObjectMapper json,
                             @Value("${restaurant.redis.key-prefix}") String prefix,
                             @Value("${restaurant.redis.default-ttl-seconds}") long defaultTtlSeconds) {
        this.redis = redis;
        this.json = json;
        this.prefix = prefix;
        this.defaultTtl = Duration.ofSeconds(defaultTtlSeconds);
    }

    public void register(Receipt receipt) {
        LocalDate day = receipt.closedAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();

        redis.opsForValue().increment(key("stats", day.toString(), "receipts"));
        redis.opsForHash().increment(key("stats", day.toString(), "revenue"), receipt.channel(),
                receipt.total().doubleValue());
        redis.opsForHash().increment(key("stats", day.toString(), "count"), receipt.channel(), 1);

        for (ReceiptItem item : receipt.items()) {
            redis.opsForZSet().incrementScore(key("top-dishes", day.toString()), item.dish(), item.qty());
        }

        String feed = key("feed");
        redis.opsForList().leftPush(feed, receipt.receiptNo() + " | " + receipt.channel() + " | " + receipt.total());
        redis.opsForList().trim(feed, 0, 49);

        cache(receipt);
    }

    public void cache(Receipt receipt) {
        try {
            redis.opsForValue().set(key("receipt", receipt.receiptNo()), json.writeValueAsString(receipt), defaultTtl);
        } catch (JacksonException e) {
            throw new DataAccessFailureException("Cannot serialize receipt " + receipt.receiptNo(), e);
        }
    }

    public Optional<Receipt> cached(String receiptNo) {
        String raw = redis.opsForValue().get(key("receipt", receiptNo));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(raw, Receipt.class));
        } catch (JacksonException e) {
            throw new DataAccessFailureException("Cannot read cached receipt " + receiptNo, e);
        }
    }

    public Long secondsToLive(String receiptNo) {
        return redis.getExpire(key("receipt", receiptNo));
    }

    public boolean evict(String receiptNo) {
        return Boolean.TRUE.equals(redis.delete(key("receipt", receiptNo)));
    }

    public Map<String, Object> counters(LocalDate day) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("day", day.toString());
        result.put("receipts", redis.opsForValue().get(key("stats", day.toString(), "receipts")));
        result.put("revenueByChannel", redis.opsForHash().entries(key("stats", day.toString(), "revenue")));
        result.put("countByChannel", redis.opsForHash().entries(key("stats", day.toString(), "count")));
        return result;
    }

    public List<Map<String, Object>> topDishes(LocalDate day, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(key("top-dishes", day.toString()), 0, limit - 1L);
        if (tuples == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            rows.add(Map.of(
                    "dish", String.valueOf(tuple.getValue()),
                    "soldQty", tuple.getScore() == null ? 0 : tuple.getScore().longValue()));
        }
        return rows;
    }

    public List<String> feed(int limit) {
        List<String> rows = redis.opsForList().range(key("feed"), 0, limit - 1L);
        return rows == null ? List.of() : rows;
    }

    private String key(String... parts) {
        return prefix + ":" + String.join(":", parts);
    }
}
