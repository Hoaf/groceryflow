package com.groceryflow.orderservice.model;

public enum OrderStatus {
    PENDING,    // vừa tạo, chờ product-service xử lý stock
    CONFIRMED,  // stock đã trừ thành công
    CANCELLED   // hết hàng, hoặc timeout, hoặc lỗi
}
