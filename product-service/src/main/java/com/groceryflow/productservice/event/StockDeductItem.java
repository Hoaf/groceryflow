package com.groceryflow.productservice.event;
import lombok.Data;
@Data
public class StockDeductItem {
    private String productId;
    private int quantity;
}
