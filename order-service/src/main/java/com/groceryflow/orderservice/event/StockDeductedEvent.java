package com.groceryflow.orderservice.event;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class StockDeductedEvent {
    private String orderId;
    private List<Map<String, Object>> deductedItems;
}
