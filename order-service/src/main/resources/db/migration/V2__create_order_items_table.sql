-- Tại sao lưu product_name + unit_price? Snapshot tại thời điểm đặt hàng.
CREATE TABLE order_items (
    id           VARCHAR(36)    PRIMARY KEY,
    order_id     VARCHAR(36)    NOT NULL,
    product_id   VARCHAR(36)    NOT NULL,
    product_name VARCHAR(255)   NOT NULL,
    quantity     INT            NOT NULL,
    unit_price   DECIMAL(12,2)  NOT NULL,
    subtotal     DECIMAL(12,2)  NOT NULL,
    INDEX idx_order_items_order_id (order_id)
);
