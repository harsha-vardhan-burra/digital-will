-- V2__create_trusted_contact_verification_tables.sql
-- Trusted Contact Verification / 2-of-3 Verification Engine (PR2)

-- Add verification cycle to wills for reset-safety (Option A)
ALTER TABLE wills ADD COLUMN verification_cycle BIGINT NOT NULL DEFAULT 0;

-- Trusted contacts (separate from heirs/beneficiaries - PR2 Sec 5)
CREATE TABLE trusted_contacts (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_trusted_contacts_email CHECK (email <> '')
);

CREATE UNIQUE INDEX idx_trusted_contacts_email ON trusted_contacts (email);
CREATE INDEX idx_trusted_contacts_created_at ON trusted_contacts (created_at);

-- Association: will <-> trusted contact (explicit relationship, Sec 7)
CREATE TABLE will_contacts (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_will_contacts_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE,
    CONSTRAINT fk_will_contacts_contact FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id) ON DELETE CASCADE,
    CONSTRAINT uq_will_contacts_will_contact UNIQUE (will_id, contact_id)
);

CREATE INDEX idx_will_contacts_will_id ON will_contacts (will_id);
CREATE INDEX idx_will_contacts_contact_id ON will_contacts (contact_id);
CREATE INDEX idx_will_contacts_active ON will_contacts (is_active);

-- Verification requests: token-based, expiry-aware, cycle-bound, single-use
CREATE TABLE verification_requests (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    verification_cycle BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_verification_requests_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE,
    CONSTRAINT fk_verification_requests_contact FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id) ON DELETE CASCADE,
    CONSTRAINT chk_verification_requests_status CHECK (status IN ('ACTIVE','CONFIRMED','EXPIRED','REVOKED')),
    CONSTRAINT chk_verification_requests_expiry CHECK (expires_at > created_at),
    CONSTRAINT uq_verification_requests_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_verification_requests_will_id ON verification_requests (will_id);
CREATE INDEX idx_verification_requests_contact_id ON verification_requests (contact_id);
CREATE INDEX idx_verification_requests_token_hash ON verification_requests (token_hash);
CREATE INDEX idx_verification_requests_status ON verification_requests (status);
CREATE INDEX idx_verification_requests_expires_at ON verification_requests (expires_at);
CREATE INDEX idx_verification_requests_cycle ON verification_requests (verification_cycle);
CREATE INDEX idx_verification_requests_will_status ON verification_requests (will_id, status);

-- Contact confirmations: database-enforced uniqueness invariant Sec 8
CREATE TABLE contact_confirmations (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    contact_id UUID NOT NULL,
    verification_request_id UUID NOT NULL,
    verification_cycle BIGINT NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_contact_confirmations_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE,
    CONSTRAINT fk_contact_confirmations_contact FOREIGN KEY (contact_id) REFERENCES trusted_contacts(id) ON DELETE CASCADE,
    CONSTRAINT fk_contact_confirmations_request FOREIGN KEY (verification_request_id) REFERENCES verification_requests(id) ON DELETE CASCADE,
    CONSTRAINT uq_contact_confirmations_will_contact_cycle UNIQUE (will_id, contact_id, verification_cycle)
);

CREATE INDEX idx_contact_confirmations_will_id ON contact_confirmations (will_id);
CREATE INDEX idx_contact_confirmations_contact_id ON contact_confirmations (contact_id);
CREATE INDEX idx_contact_confirmations_request_id ON contact_confirmations (verification_request_id);
CREATE INDEX idx_contact_confirmations_cycle ON contact_confirmations (verification_cycle);
CREATE INDEX idx_contact_confirmations_will_cycle ON contact_confirmations (will_id, verification_cycle);
