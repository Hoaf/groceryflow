package com.groceryflow.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groceryflow.orderservice.dto.request.CreateOrderRequest;
import com.groceryflow.orderservice.dto.request.OrderItemRequest;
import com.groceryflow.orderservice.dto.response.OrderResponse;
import com.groceryflow.orderservice.model.*;
import com.groceryflow.orderservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    // ── Step 2.7: createOrder ──────────────────────────────────
    // Tại sao 2 outbox events trong cùng 1 transaction?
    //   order.created → downstream (report, notification, audit)
    //   stock.deduct.requested → trigger Saga cho product-service
    //   Cùng 1 tx → commit cả 2 hoặc rollback cả 2 → không bị treo PENDING
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req) {
        BigDecimal total = req.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(req.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .build();
        orderRepository.save(order);

        List<OrderItem> items = req.getItems().stream()
                .map(i -> buildOrderItem(order.getId(), i))
                .toList();
        orderItemRepository.saveAll(items);

        outboxEventRepository.save(OutboxEvent.of("order.created",
                toJson(buildOrderCreatedPayload(order, items.size()))));
        outboxEventRepository.save(OutboxEvent.of("stock.deduct.requested",
                toJson(buildDeductPayload(order.getId(), items))));

        log.info("Order created: orderId={}, userId={}, total={}, items={}",
                order.getId(), order.getUserId(), total, items.size());
        return OrderResponse.from(order, items);
    }

    // ── Step 2.9: confirmOrder ─────────────────────────────────
    @Transactional
    public void confirmOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        outboxEventRepository.save(OutboxEvent.of("order.confirmed",
                toJson(buildStatusPayload(order))));
        log.info("Order confirmed: orderId={}", orderId);
    }

    // ── Step 2.9: cancelOrder ──────────────────────────────────
    @Transactional
    public void cancelOrder(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        outboxEventRepository.save(OutboxEvent.of("order.cancelled",
                toJson(buildStatusPayload(order))));
        log.warn("Order cancelled: orderId={}, reason={}", orderId, reason);
    }

    // ── Timeout job ────────────────────────────────────────────
    // Tại sao cần? product-service có thể down → Order mãi PENDING
    // → @Scheduled cancel sau 10 phút
    @Scheduled(fixedDelay = 60_000)
    public void timeoutStaleOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        List<Order> stale = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING, cutoff);
        if (!stale.isEmpty()) {
            log.warn("Timing out {} stale PENDING orders", stale.size());
        }
        stale.forEach(o -> cancelOrder(o.getId(), "Order timeout after 10 minutes"));
    }

    // ── Helpers ────────────────────────────────────────────────

    private OrderItem buildOrderItem(String orderId, OrderItemRequest req) {
        BigDecimal subtotal = req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity()));
        return OrderItem.builder()
                .orderId(orderId)
                .productId(req.getProductId())
                .productName(req.getProductName())
                .quantity(req.getQuantity())
                .unitPrice(req.getUnitPrice())
                .subtotal(subtotal)
                .build();
    }

    private Map<String, Object> buildOrderCreatedPayload(Order order, int itemCount) {
        Map<String, Object> p = new HashMap<>();
        p.put("orderId", order.getId());
        p.put("userId", order.getUserId());
        p.put("totalAmount", order.getTotalAmount());
        p.put("itemCount", itemCount);
        return p;
    }

    private Map<String, Object> buildDeductPayload(String orderId, List<OrderItem> items) {
        List<Map<String, Object>> itemList = items.stream().map(item -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", item.getProductId());
            m.put("quantity", item.getQuantity());
            return m;
        }).toList();
        Map<String, Object> p = new HashMap<>();
        p.put("orderId", orderId);
        p.put("items", itemList);
        return p;
    }

    private Map<String, Object> buildStatusPayload(Order order) {
        Map<String, Object> p = new HashMap<>();
        p.put("orderId", order.getId());
        p.put("userId", order.getUserId());
        return p;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }
}
