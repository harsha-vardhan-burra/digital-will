-- V7__create_users_and_auth_tables.sql
-- User authentication and session token storage for Phase 3

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_email CHECK (email <> '')
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE user_auth_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_user_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_auth_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_user_auth_tokens_user_id ON user_auth_tokens (user_id);
CREATE INDEX idx_user_auth_tokens_hash ON user_auth_tokens (token_hash);
CREATE INDEX idx_user_auth_tokens_expires ON user_auth_tokens (expires_at);
