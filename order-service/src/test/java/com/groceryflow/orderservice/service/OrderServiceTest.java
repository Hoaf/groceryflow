package com.groceryflow.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.groceryflow.orderservice.dto.request.CreateOrderRequest;
import com.groceryflow.orderservice.dto.request.OrderItemRequest;
import com.groceryflow.orderservice.dto.response.OrderResponse;
import com.groceryflow.orderservice.model.Order;
import com.groceryflow.orderservice.model.OrderItem;
import com.groceryflow.orderservice.model.OrderStatus;
import com.groceryflow.orderservice.model.OutboxEvent;
import com.groceryflow.orderservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        orderService = new OrderService(
                orderRepository, orderItemRepository,
                outboxEventRepository, processedEventRepository, objectMapper);
    }

    private CreateOrderRequest buildRequest() {
        OrderItemRequest item1 = new OrderItemRequest();
        item1.setProductId("p1");
        item1.setProductName("Mì gói");
        item1.setQuantity(2);
        item1.setUnitPrice(new BigDecimal("5000"));

        OrderItemRequest item2 = new OrderItemRequest();
        item2.setProductId("p2");
        item2.setProductName("Nước mắm");
        item2.setQuantity(1);
        item2.setUnitPrice(new BigDecimal("30000"));

        CreateOrderRequest req = new CreateOrderRequest();
        req.setUserId("user-123");
        req.setItems(List.of(item1, item2));
        return req;
    }

    @Test
    void createOrder_shouldSaveOrderWithPendingStatus() {
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.createOrder(buildRequest());

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getUserId()).isEqualTo("user-123");
    }

    @Test
    void createOrder_shouldCalculateTotalAmountCorrectly() {
        // 2 * 5000 + 1 * 30000 = 40000
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.createOrder(buildRequest());

        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("40000"));
    }

    @Test
    void createOrder_shouldSaveTwoOutboxEvents() {
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArgument(0));

        orderService.createOrder(buildRequest());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(captor.capture());
        List<String> topics = captor.getAllValues().stream().map(OutboxEvent::getTopic).toList();
        assertThat(topics).containsExactlyInAnyOrder("order.created", "stock.deduct.requested");
    }

    @Test
    void confirmOrder_shouldUpdateStatusToConfirmed() {
        Order order = Order.builder().id("o1").userId("u1")
                .status(OrderStatus.PENDING).totalAmount(BigDecimal.TEN)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderService.confirmOrder("o1");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancelOrder_shouldUpdateStatusToCancelled() {
        Order order = Order.builder().id("o1").userId("u1")
                .status(OrderStatus.PENDING).totalAmount(BigDecimal.TEN)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        orderService.cancelOrder("o1", "Insufficient stock");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
}
