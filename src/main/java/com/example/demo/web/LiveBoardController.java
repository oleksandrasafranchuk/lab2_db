package com.example.demo.web;

import com.example.demo.model.Receipt;
import com.example.demo.nosql.redis.RedisReceiptBoard;
import com.example.demo.uow.ResourceNotFoundException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/live")
public class LiveBoardController {

    private final RedisReceiptBoard board;

    public LiveBoardController(RedisReceiptBoard board) {
        this.board = board;
    }

    @GetMapping("/counters")
    public Map<String, Object> counters(@RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return board.counters(day == null ? LocalDate.now() : day);
    }

    @GetMapping("/top-dishes")
    public List<Map<String, Object>> topDishes(@RequestParam(required = false)
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                               @RequestParam(defaultValue = "5") int limit) {
        return board.topDishes(day == null ? LocalDate.now() : day, limit);
    }

    @GetMapping("/feed")
    public List<String> feed(@RequestParam(defaultValue = "10") int limit) {
        return board.feed(limit);
    }

    @GetMapping("/cache/{receiptNo}")
    public Map<String, Object> cached(@PathVariable String receiptNo) {
        Receipt receipt = board.cached(receiptNo)
                .orElseThrow(() -> ResourceNotFoundException.of("Cached receipt", receiptNo));
        return Map.of(
                "secondsToLive", board.secondsToLive(receiptNo),
                "receipt", receipt);
    }
}
