package com.groceryflow.orderservice.dto.response;

import com.groceryflow.orderservice.model.Order;
import com.groceryflow.orderservice.model.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private String id;
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private List<OrderItemResponse> items;
    private LocalDateTime createdAt;

    public static OrderResponse from(Order order, List<OrderItem> items) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .items(items.stream().map(OrderItemResponse::from).toList())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
