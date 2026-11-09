package com.example.demo.web;

import com.example.demo.model.ChannelStatsView;
import com.example.demo.model.Receipt;
import com.example.demo.model.ReceiptRef;
import com.example.demo.model.TopDishView;
import com.example.demo.service.ReceiptService;
import com.example.demo.web.dto.ReceiptArchiveRequest;
import com.example.demo.web.dto.ReceiptRatingRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptService service;

    public ReceiptController(ReceiptService service) {
        this.service = service;
    }

    @PostMapping("/from-order/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Receipt archive(@PathVariable long orderId,
                           @Valid @RequestBody ReceiptArchiveRequest request) {
        return service.archive(orderId, request);
    }

    @GetMapping("/{receiptNo}")
    public Receipt byNo(@PathVariable String receiptNo) {
        return service.byNo(receiptNo);
    }

    @GetMapping
    public List<Receipt> customerHistory(@RequestParam long customerId,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                         OffsetDateTime from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                         OffsetDateTime to) {
        return service.customerHistory(customerId, from, to);
    }

    @GetMapping("/top-dishes")
    public List<TopDishView> topDishes(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                       OffsetDateTime from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                       OffsetDateTime to,
                                       @RequestParam(defaultValue = "5") int limit) {
        return service.topDishes(from, to, limit);
    }

    @GetMapping("/by-modifier")
    public List<ReceiptRef> byModifier(@RequestParam String name,
                                       @RequestParam(defaultValue = "50") int limit) {
        return service.byModifier(name, limit);
    }

    @GetMapping("/channel-stats")
    public List<ChannelStatsView> channelStats(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                               OffsetDateTime from,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                               OffsetDateTime to) {
        return service.channelStats(from, to);
    }

    @PostMapping("/{receiptNo}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@PathVariable String receiptNo, @Valid @RequestBody ReceiptRatingRequest request) {
        service.rate(receiptNo, request.rating(), request.comment());
    }
}
