CREATE TABLE products (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    barcode     VARCHAR(50) UNIQUE,
    price       DECIMAL(10,2) NOT NULL,
    unit        VARCHAR(20) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    is_active   TINYINT(1) DEFAULT 1,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    INDEX idx_products_barcode (barcode),
    INDEX idx_products_category_id (category_id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id)
);
