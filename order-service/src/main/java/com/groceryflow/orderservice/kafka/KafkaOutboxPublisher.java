package com.groceryflow.orderservice.kafka;

import com.groceryflow.orderservice.model.OutboxEvent;
import com.groceryflow.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// KafkaOutboxPublisher: background job đọc outbox_events chưa publish và gửi lên Kafka.
//
// Tại sao @Scheduled thay vì publish trực tiếp?
//   Outbox Pattern: ghi DB + outbox trong 1 transaction.
//   Publisher chạy độc lập → nếu Kafka down, publisher retry tự động khi Kafka recover.
//   Nếu publisher crash sau khi publish nhưng trước khi mark published=true:
//   → published vẫn = false → publish lại khi restart
//   → Consumer phải idempotent (processed_events) để handle duplicate.
//
// fixedDelay vs fixedRate:
//   fixedDelay: tính từ khi job XONG → an toàn, tránh concurrent run nếu job chậm.
//   fixedRate: tính từ khi job BẮT ĐẦU → có thể concurrent nếu job chậm hơn interval.
//   → Dùng fixedDelay cho Outbox publisher.
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 1000) // mỗi 1 giây sau khi job trước xong
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop100ByPublishedFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getId(), event.getPayload()).get();
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
