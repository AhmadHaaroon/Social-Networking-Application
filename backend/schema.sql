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

CREATE TABLE IF NOT EXISTS friendships (
    user_a CHAR(36) NOT NULL,
    user_b CHAR(36) NOT NULL,
    requester CHAR(36) NOT NULL,
    state ENUM('pending', 'accepted') NOT NULL,
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (user_a, user_b),
    FOREIGN KEY (user_a) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (user_b) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS close_friends (
    owner_id CHAR(36) NOT NULL,
    friend_id CHAR(36) NOT NULL,
    PRIMARY KEY (owner_id, friend_id),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS media (
    id CHAR(36) PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    mime VARCHAR(80) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS posts (
    id CHAR(36) PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    body LONGTEXT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS comments (
    id CHAR(36) PRIMARY KEY,
    post_id CHAR(36) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    parent_id CHAR(36) NULL,
    body LONGTEXT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS reactions (
    post_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    reaction VARCHAR(12) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS comment_likes (
    comment_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    PRIMARY KEY (comment_id, user_id),
    FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS stories (
    id CHAR(36) PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    audience VARCHAR(10) NOT NULL,
    body LONGTEXT NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX (expires_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS story_views (
    story_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    viewed_at BIGINT NOT NULL,
    PRIMARY KEY (story_id, user_id),
    FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS content_lock (id INT PRIMARY KEY) ENGINE=InnoDB;
INSERT IGNORE INTO content_lock VALUES (1);

CREATE TABLE IF NOT EXISTS messages (
    id CHAR(36) PRIMARY KEY,
    sender_id CHAR(36) NOT NULL,
    recipient_id CHAR(36) NOT NULL,
    body LONGTEXT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX (recipient_id, created_at), INDEX (sender_id, created_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS chat_reads (
    owner_id CHAR(36) NOT NULL, other_id CHAR(36) NOT NULL,
    read_at BIGINT NOT NULL DEFAULT 0, hidden_at BIGINT NOT NULL DEFAULT 0, typing_until BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (owner_id, other_id),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (other_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS calls (
    id CHAR(36) PRIMARY KEY, caller_id CHAR(36) NOT NULL, callee_id CHAR(36) NOT NULL,
    body LONGTEXT NOT NULL, updated_at BIGINT NOT NULL,
    FOREIGN KEY (caller_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (callee_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS notifications (
    id CHAR(36) PRIMARY KEY, owner_id CHAR(36) NOT NULL, body LONGTEXT NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS devices (
    token VARCHAR(512) PRIMARY KEY, owner_id CHAR(36) NOT NULL, session_hash CHAR(64) NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS push_outbox (
    id CHAR(36) PRIMARY KEY, owner_id CHAR(36) NOT NULL, body LONGTEXT NOT NULL,
    created_at BIGINT NOT NULL, attempts INT NOT NULL DEFAULT 0,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
