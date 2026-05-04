package com.groceryflow.productservice.event;
import lombok.Data;
import java.util.List;
@Data
public class StockDeductRequestedEvent {
    private String orderId;
    private List<StockDeductItem> items;
}
