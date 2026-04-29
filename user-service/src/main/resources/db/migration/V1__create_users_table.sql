CREATE TABLE users (
    id          VARCHAR(36)                 NOT NULL,
    username    VARCHAR(50)                 NOT NULL,
    password    VARCHAR(255)                NOT NULL,
    role        ENUM('OWNER', 'STAFF')      NOT NULL,
    is_active   TINYINT(1)                  NOT NULL DEFAULT 1,
    created_at  DATETIME                    NOT NULL,
    updated_at  DATETIME                    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE INDEX idx_users_username (username)
);

-- Seed: OWNER account để login lần đầu
-- Password plain text: 'admin123'
INSERT INTO users (id, username, password, role, is_active, created_at, updated_at)
VALUES (
    UUID(),
    'admin',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'OWNER',
    1,
    NOW(),
    NOW()
);
