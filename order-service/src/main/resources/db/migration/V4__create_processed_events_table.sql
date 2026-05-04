-- Idempotency: Kafka at-least-once → cùng event có thể được deliver nhiều lần.
CREATE TABLE processed_events (
    event_id     VARCHAR(36)    PRIMARY KEY,
    processed_at DATETIME       NOT NULL
);
