-- V8__document_validation_and_asset_constraints.sql
-- Document validation lifecycle status and structured asset integrity constraints

-- 1. Document validation pipeline tracking
ALTER TABLE encrypted_documents ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACCEPTED';
ALTER TABLE encrypted_documents ADD COLUMN status_code VARCHAR(100);
ALTER TABLE encrypted_documents ADD COLUMN status_message VARCHAR(500);
ALTER TABLE encrypted_documents ADD COLUMN detected_content_type VARCHAR(100);
ALTER TABLE encrypted_documents ADD COLUMN category VARCHAR(50) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE encrypted_documents ADD CONSTRAINT chk_encrypted_documents_status 
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'NEEDS_REVIEW', 'FAILED_RETRYABLE'));

CREATE INDEX idx_encrypted_documents_status ON encrypted_documents (status);
CREATE INDEX idx_encrypted_documents_will_status ON encrypted_documents (will_id, status);

-- 2. Structured Asset attributes and constraints
ALTER TABLE assets ADD COLUMN institution_provider VARCHAR(255);
ALTER TABLE assets ADD COLUMN account_type VARCHAR(100);
ALTER TABLE assets ADD COLUMN currency VARCHAR(3);
ALTER TABLE assets ADD COLUMN asset_value NUMERIC(19, 4);
ALTER TABLE assets ADD COLUMN valuation_date DATE;
ALTER TABLE assets ADD COLUMN ownership_percentage NUMERIC(5, 2);
ALTER TABLE assets ADD COLUMN asset_year INT;
ALTER TABLE assets ADD COLUMN asset_url VARCHAR(2000);
ALTER TABLE assets ADD COLUMN identifier_masked VARCHAR(255);

ALTER TABLE assets ADD CONSTRAINT chk_assets_currency CHECK (currency IS NULL OR LENGTH(currency) = 3);
ALTER TABLE assets ADD CONSTRAINT chk_assets_value CHECK (asset_value IS NULL OR asset_value >= 0);
ALTER TABLE assets ADD CONSTRAINT chk_assets_ownership CHECK (ownership_percentage IS NULL OR (ownership_percentage > 0 AND ownership_percentage <= 100));
ALTER TABLE assets ADD CONSTRAINT chk_assets_year CHECK (asset_year IS NULL OR (asset_year >= 1800 AND asset_year <= 2100));

CREATE INDEX idx_assets_will_title ON assets (will_id, title);
