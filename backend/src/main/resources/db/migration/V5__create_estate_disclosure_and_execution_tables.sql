-- V5__create_estate_disclosure_and_execution_tables.sql
-- Estate Assets, Beneficiaries, Allocations, Release Execution Tracking and Controlled Disclosure (PR5)

CREATE TABLE assets (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    encrypted_access_data TEXT,
    instructions TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_assets_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE
);

CREATE INDEX idx_assets_will_id ON assets (will_id);
CREATE INDEX idx_assets_category ON assets (category);

CREATE TABLE beneficiaries (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    relationship VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_beneficiaries_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE
);

CREATE INDEX idx_beneficiaries_will_id ON beneficiaries (will_id);
CREATE INDEX idx_beneficiaries_email ON beneficiaries (email);

CREATE TABLE asset_allocations (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL,
    beneficiary_id UUID NOT NULL,
    share_percentage INT NOT NULL DEFAULT 100,
    instructions TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_allocations_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_allocations_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(id) ON DELETE CASCADE,
    CONSTRAINT chk_allocations_percentage CHECK (share_percentage > 0 AND share_percentage <= 100),
    CONSTRAINT uq_asset_beneficiary UNIQUE (asset_id, beneficiary_id)
);

CREATE INDEX idx_allocations_asset_id ON asset_allocations (asset_id);
CREATE INDEX idx_allocations_beneficiary_id ON asset_allocations (beneficiary_id);

CREATE TABLE release_executions (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_items INT NOT NULL DEFAULT 0,
    completed_items INT NOT NULL DEFAULT 0,
    failed_items INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    CONSTRAINT fk_release_executions_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE,
    CONSTRAINT chk_release_executions_status CHECK (status IN (
        'NOT_STARTED', 'IN_PROGRESS', 'PARTIALLY_COMPLETED', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_PERMANENT'
    ))
);

CREATE INDEX idx_release_executions_will_id ON release_executions (will_id);
CREATE INDEX idx_release_executions_status ON release_executions (status);
CREATE INDEX idx_release_executions_last_attempt ON release_executions (last_attempt_at);

CREATE TABLE release_execution_items (
    id UUID PRIMARY KEY,
    execution_id UUID NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_release_items_execution FOREIGN KEY (execution_id) REFERENCES release_executions(id) ON DELETE CASCADE,
    CONSTRAINT chk_release_items_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT uq_execution_target_item UNIQUE (execution_id, target_id, item_type)
);

CREATE INDEX idx_release_items_execution_id ON release_execution_items (execution_id);
CREATE INDEX idx_release_items_target ON release_execution_items (target_id);
CREATE INDEX idx_release_items_status ON release_execution_items (status);

CREATE TABLE disclosure_tokens (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    beneficiary_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    first_accessed_at TIMESTAMP WITH TIME ZONE,
    last_accessed_at TIMESTAMP WITH TIME ZONE,
    access_count INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_disclosure_tokens_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE,
    CONSTRAINT fk_disclosure_tokens_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(id) ON DELETE CASCADE,
    CONSTRAINT chk_disclosure_tokens_status CHECK (status IN ('ACTIVE', 'ACCESSED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT uq_disclosure_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_disclosure_tokens_will_id ON disclosure_tokens (will_id);
CREATE INDEX idx_disclosure_tokens_beneficiary_id ON disclosure_tokens (beneficiary_id);
CREATE INDEX idx_disclosure_tokens_hash ON disclosure_tokens (token_hash);
CREATE INDEX idx_disclosure_tokens_status ON disclosure_tokens (status);
CREATE INDEX idx_disclosure_tokens_expires_at ON disclosure_tokens (expires_at);

CREATE TABLE disclosed_records (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    beneficiary_id UUID NOT NULL,
    disclosure_token_id UUID NOT NULL,
    package_payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_disclosed_records_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE,
    CONSTRAINT fk_disclosed_records_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries(id) ON DELETE CASCADE,
    CONSTRAINT fk_disclosed_records_token FOREIGN KEY (disclosure_token_id) REFERENCES disclosure_tokens(id) ON DELETE CASCADE,
    CONSTRAINT uq_disclosed_beneficiary_will UNIQUE (will_id, beneficiary_id)
);

CREATE INDEX idx_disclosed_records_will_id ON disclosed_records (will_id);
CREATE INDEX idx_disclosed_records_beneficiary_id ON disclosed_records (beneficiary_id);
