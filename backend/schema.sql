CREATE DATABASE IF NOT EXISTS kinnect CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kinnect;

CREATE TABLE IF NOT EXISTS users (
    id CHAR(36) PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    username VARCHAR(30) NULL UNIQUE,
    profile LONGTEXT NOT NULL,
    profile_complete BOOLEAN NOT NULL DEFAULT FALSE,
    online BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sessions (
    token_hash CHAR(64) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    expires_at BIGINT NOT NULL,
    last_seen BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX (user_id)
) ENGINE=InnoDB;

-- The receipt and the domain change are committed together.
CREATE TABLE IF NOT EXISTS action_receipts (
    user_id CHAR(36) NOT NULL,
    action_id CHAR(36) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response LONGTEXT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, action_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
