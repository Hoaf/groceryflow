-- Outbox Pattern: ghi vào bảng này cùng transaction với business data.
-- Background job đọc published=false và publish lên Kafka.
CREATE TABLE outbox_events (
    id           VARCHAR(36)    PRIMARY KEY,
    topic        VARCHAR(100)   NOT NULL,
    payload      JSON           NOT NULL,
    published    BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at   DATETIME       NOT NULL,
    published_at DATETIME,
    INDEX idx_outbox_published (published)
);
