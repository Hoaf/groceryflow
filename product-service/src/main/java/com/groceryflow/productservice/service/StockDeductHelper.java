package com.groceryflow.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groceryflow.productservice.event.StockDeductItem;
import com.groceryflow.productservice.event.StockDeductRequestedEvent;
import com.groceryflow.productservice.exception.InsufficientStockException;
import com.groceryflow.productservice.model.OutboxEvent;
import com.groceryflow.productservice.model.Stock;
import com.groceryflow.productservice.repository.OutboxEventRepository;
import com.groceryflow.productservice.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockDeductHelper {

    private final StockRepository stockRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    private static final int LOW_STOCK_THRESHOLD = 5;

    @Transactional
    public void deductAllInTransaction(StockDeductRequestedEvent event) {
        for (StockDeductItem item : event.getItems()) {
            Stock stock = stockRepository.findByProductId(item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Stock not found for product: " + item.getProductId()));

            if (stock.getQuantity() < item.getQuantity()) {
                throw new InsufficientStockException(
                        item.getProductId(), stock.getQuantity(), item.getQuantity());
            }

            stock.setQuantity(stock.getQuantity() - item.getQuantity());
            stockRepository.save(stock);

            log.info("Stock deducted: productId={}, deducted={}, remaining={}, orderId={}",
                    item.getProductId(), item.getQuantity(), stock.getQuantity(), event.getOrderId());

            if (stock.getQuantity() <= LOW_STOCK_THRESHOLD) {
                outboxEventRepository.save(OutboxEvent.of("stock.low",
                        toJson(buildLowStockPayload(stock))));
            }
        }
        outboxEventRepository.save(OutboxEvent.of("stock.deducted",
                toJson(buildDeductedPayload(event))));
    }

    @Transactional
    public void saveDeductFailedEvent(String orderId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("reason", reason);
        outboxEventRepository.save(OutboxEvent.of("stock.deduct.failed", toJson(payload)));
        log.warn("Stock deduct failed outbox saved: orderId={}, reason={}", orderId, reason);
    }

    private Map<String, Object> buildDeductedPayload(StockDeductRequestedEvent event) {
        List<Map<String, Object>> deductedItems = event.getItems().stream().map(item -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", item.getProductId());
            m.put("quantity", item.getQuantity());
            return m;
        }).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", event.getOrderId());
        payload.put("deductedItems", deductedItems);
        return payload;
    }

    private Map<String, Object> buildLowStockPayload(Stock stock) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", stock.getProductId());
        payload.put("currentQuantity", stock.getQuantity());
        payload.put("threshold", LOW_STOCK_THRESHOLD);
        return payload;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }
}
