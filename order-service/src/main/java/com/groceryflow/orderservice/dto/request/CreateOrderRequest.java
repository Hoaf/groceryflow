package com.groceryflow.orderservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @Valid
    @NotEmpty(message = "order must have at least one item")
    private List<OrderItemRequest> items;
}
