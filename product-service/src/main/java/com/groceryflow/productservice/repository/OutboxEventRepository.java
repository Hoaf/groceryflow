package com.groceryflow.productservice.repository;
import com.groceryflow.productservice.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
