package com.groceryflow.productservice.kafka;

import com.groceryflow.productservice.model.OutboxEvent;
import com.groceryflow.productservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    // NO @Transactional — each save() commits per-event independently
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop100ByPublishedFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getId(), event.getPayload()).get(); // block for ack
                event.setPublished(true);
                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                log.debug("Published outbox event: id={}, topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.warn("Failed to publish outbox event: id={}, topic={}, error={}",
                        event.getId(), event.getTopic(), e.getMessage());
            }
        }
    }
}
