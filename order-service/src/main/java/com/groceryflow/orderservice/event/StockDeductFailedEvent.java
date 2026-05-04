package com.groceryflow.orderservice.event;

import lombok.Data;

@Data
public class StockDeductFailedEvent {
    private String orderId;
    private String reason;
}
