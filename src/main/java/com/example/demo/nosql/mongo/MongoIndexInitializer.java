package com.example.demo.nosql.mongo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MongoIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexInitializer.class);

    private final MongoReceiptRepository receipts;

    public MongoIndexInitializer(MongoReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            receipts.ensureIndexes();
            log.info("MongoDB receipt indexes are in place, documents: {}", receipts.count());
        } catch (RuntimeException e) {
            log.warn("MongoDB is not available at startup: {}", e.getMessage());
        }
    }
}
