CREATE TABLE orders (
    id           VARCHAR(36)    PRIMARY KEY,
    user_id      VARCHAR(36)    NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(12,2)  NOT NULL,
    created_at   DATETIME       NOT NULL,
    updated_at   DATETIME       NOT NULL,
    INDEX idx_orders_user_id (user_id),
    INDEX idx_orders_status (status)
);
