CREATE TABLE stocks (
    id         VARCHAR(36) PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL UNIQUE,
    quantity   INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_stocks_product FOREIGN KEY (product_id) REFERENCES products(id)
);
