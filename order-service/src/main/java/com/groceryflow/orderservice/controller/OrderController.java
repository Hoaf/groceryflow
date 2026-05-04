package com.groceryflow.orderservice.controller;

import com.groceryflow.orderservice.dto.request.CreateOrderRequest;
import com.groceryflow.orderservice.dto.response.ApiResponse;
import com.groceryflow.orderservice.dto.response.OrderResponse;
import com.groceryflow.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    // POST /api/orders — trả 202 ACCEPTED (async Saga đang chạy)
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Order created, processing payment", response));
    }

    // GET /api/orders/{id} — poll status (PENDING → CONFIRMED/CANCELLED)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Order retrieved",
                orderService.getOrder(id)));
    }
}
