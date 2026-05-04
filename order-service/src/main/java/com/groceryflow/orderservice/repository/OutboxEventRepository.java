package com.groceryflow.orderservice.repository;

import com.groceryflow.orderservice.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByPublishedFalse();
}
