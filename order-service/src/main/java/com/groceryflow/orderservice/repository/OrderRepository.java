package com.groceryflow.orderservice.repository;

import com.groceryflow.orderservice.model.Order;
import com.groceryflow.orderservice.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);
}
