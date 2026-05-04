package com.groceryflow.orderservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groceryflow.orderservice.event.StockDeductedEvent;
import com.groceryflow.orderservice.event.StockDeductFailedEvent;
import com.groceryflow.orderservice.model.ProcessedEvent;
import com.groceryflow.orderservice.repository.ProcessedEventRepository;
import com.groceryflow.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// StockEventConsumer — consume Saga results from product-service.
//
// stock.deducted   → confirmOrder (happy path)
// stock.deduct.failed → cancelOrder (compensating action)
//
// Idempotency: check processed_events before handling.
// If processing fails (e.g. deserialization error): do NOT mark processed
// → Kafka will redeliver → retry opportunity.
@Component
@RequiredArgsConstructor
@Slf4j
public class StockEventConsumer {

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"stock.deducted", "stock.deduct.failed"}, groupId = "order-service")
    public void consume(ConsumerRecord<String, String> record) {
        String eventId = record.key();

        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate event skipped: topic={}, eventId={}", record.topic(), eventId);
            return;
        }

        try {
            if ("stock.deducted".equals(record.topic())) {
                StockDeductedEvent event = objectMapper.readValue(
                        record.value(), StockDeductedEvent.class);
                log.info("Stock deducted for order: {}", event.getOrderId());
                orderService.confirmOrder(event.getOrderId());

            } else if ("stock.deduct.failed".equals(record.topic())) {
                StockDeductFailedEvent event = objectMapper.readValue(
                        record.value(), StockDeductFailedEvent.class);
                log.warn("Stock deduct failed for order: {}, reason: {}",
                        event.getOrderId(), event.getReason());
                orderService.cancelOrder(event.getOrderId(), event.getReason());
            }

            // Mark processed only on success
            processedEventRepository.save(ProcessedEvent.of(eventId));

        } catch (Exception e) {
            log.error("Failed to process event: topic={}, eventId={}, error={}",
                    record.topic(), eventId, e.getMessage(), e);
            // Do NOT mark processed → Kafka will redeliver
        }
    }
}
