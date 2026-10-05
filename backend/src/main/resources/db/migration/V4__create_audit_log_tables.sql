-- V4__create_audit_log_tables.sql
-- Tamper-Evident Hash-Chained Audit Logging (PR4)

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    sequence_number BIGINT NOT NULL,
    will_id UUID,
    actor_id VARCHAR(100) NOT NULL,
    actor_type VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100),
    details_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    prev_hash VARCHAR(64) NOT NULL,
    entry_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_audit_logs_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE,
    CONSTRAINT uq_audit_sequence UNIQUE (sequence_number)
);

CREATE INDEX idx_audit_logs_will_id ON audit_logs (will_id);
CREATE INDEX idx_audit_logs_sequence ON audit_logs (sequence_number);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id);
