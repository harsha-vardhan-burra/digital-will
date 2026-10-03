-- V1__create_wills_table.sql
-- Establishes the authoritative wills table and state constraints for Digital Will.

CREATE TABLE wills (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    state VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_verified_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    warning_sent_at TIMESTAMP WITH TIME ZONE,
    final_warning_sent_at TIMESTAMP WITH TIME ZONE,
    verification_started_at TIMESTAMP WITH TIME ZONE,
    verified_at TIMESTAMP WITH TIME ZONE,
    release_after TIMESTAMP WITH TIME ZONE,
    executing_at TIMESTAMP WITH TIME ZONE,
    executed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_wills_state CHECK (state IN (
        'ACTIVE',
        'INACTIVITY_WARNING',
        'FINAL_WARNING',
        'VERIFICATION_PENDING',
        'VERIFIED',
        'RELEASE_PENDING',
        'EXECUTING',
        'EXECUTED'
    ))
);

CREATE INDEX idx_wills_owner_id ON wills (owner_id);
CREATE INDEX idx_wills_state ON wills (state);
CREATE INDEX idx_wills_last_verified_activity ON wills (last_verified_activity_at);
CREATE INDEX idx_wills_release_after ON wills (release_after);
CREATE INDEX idx_wills_executing_at ON wills (executing_at);
