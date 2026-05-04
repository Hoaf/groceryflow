package com.groceryflow.productservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groceryflow.productservice.event.StockDeductRequestedEvent;
import com.groceryflow.productservice.model.ProcessedEvent;
import com.groceryflow.productservice.repository.ProcessedEventRepository;
import com.groceryflow.productservice.service.StockSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockSagaConsumer {

    private final StockSagaService stockSagaService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "stock.deduct.requested", groupId = "product-service")
    public void consume(ConsumerRecord<String, String> record) {
        String eventId = record.key();

        if (processedEventRepository.existsById(eventId)) {
            log.info("Duplicate stock.deduct.requested skipped: eventId={}", eventId);
            return;
        }

        try {
            StockDeductRequestedEvent event = objectMapper.readValue(
                    record.value(), StockDeductRequestedEvent.class);
            log.info("Processing stock.deduct.requested: orderId={}, items={}",
                    event.getOrderId(), event.getItems().size());
            stockSagaService.processDeductRequest(event);
        } catch (Exception e) {
            log.error("Failed to process stock.deduct.requested: eventId={}, error={}",
                    eventId, e.getMessage(), e);
        } finally {
            processedEventRepository.save(ProcessedEvent.of(eventId));
        }
    }
}
