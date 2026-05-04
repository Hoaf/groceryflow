package com.groceryflow.productservice.exception;
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productId, int available, int requested) {
        super(String.format("Insufficient stock for product: %s (available: %d, requested: %d)",
                productId, available, requested));
    }
}
