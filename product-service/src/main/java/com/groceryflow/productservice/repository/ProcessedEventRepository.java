package com.groceryflow.productservice.repository;
import com.groceryflow.productservice.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
