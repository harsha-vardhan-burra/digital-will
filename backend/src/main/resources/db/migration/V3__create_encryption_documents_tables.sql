-- V3__create_encryption_documents_tables.sql
-- Envelope Encryption and Document Persistence Metadata (PR3)

CREATE TABLE encrypted_documents (
    id UUID PRIMARY KEY,
    will_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    encrypted_dek VARCHAR(500) NOT NULL,
    iv VARCHAR(100) NOT NULL,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'AES/GCM/NoPadding',
    key_wrap_algorithm VARCHAR(50) NOT NULL DEFAULT 'AESWrap',
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    CONSTRAINT fk_encrypted_documents_will FOREIGN KEY (will_id) REFERENCES wills(id) ON DELETE CASCADE,
    CONSTRAINT chk_encrypted_documents_size CHECK (file_size >= 0),
    CONSTRAINT chk_encrypted_documents_version CHECK (version >= 1)
);

CREATE INDEX idx_encrypted_documents_will_id ON encrypted_documents (will_id);
CREATE INDEX idx_encrypted_documents_created_at ON encrypted_documents (created_at);
CREATE INDEX idx_encrypted_documents_checksum ON encrypted_documents (checksum_sha256);
