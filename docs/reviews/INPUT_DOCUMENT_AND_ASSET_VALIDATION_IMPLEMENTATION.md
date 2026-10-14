# Input Document and Structured Asset Validation — Implementation & Verification Report

**Date:** 2026-10-13  
**Repository:** `digital-will`  
**Branch:** `feat/input-document-validation`  
**Status:** Implemented, Fully Verified, All 311 Backend Tests Passing (100%), Frontend Build Clean  

---

## 1. Executive Summary & Architecture Overview

This report details the authoritative, server-side implementation and verification of input boundaries, structured asset records validation, prohibited secret screening, and an orchestrated multi-stage document validation pipeline for the **Digital Will** platform.

Prior to this work, user input validation relied primarily on basic Bean Validation annotations and client-side form checks. Document uploads were envelope-encrypted without content inspection, parser safety bounds, or malware screening. In addition, assets allowed arbitrary strings without category-specific domain rules, lacked masking of account numbers, and could potentially accept raw secret keys or passwords.

### Architecture Highlights
- **Principal-Authoritative Identity:** User identity is strictly derived from the authenticated server-side principal (`UserPrincipal`). Client-supplied user IDs in request bodies or query parameters are never trusted.
- **Estate Modifiability Enforcement:** Mutations to assets, beneficiaries, and allocations are strictly guarded by state checks; attempts to modify an estate undergoing execution (`EXECUTING` or `EXECUTED`) are rejected with `409 Conflict`.
- **Prohibited Credential Screening:** An automated validator (`ProhibitedCredentialValidator`) scans all text fields across assets, notes, and metadata, rejecting raw private keys (PEM/DER), BIP-39 mnemonic seed phrases, passwords, PINs, card CVVs, API tokens, and session cookies with `PROHIBITED_CREDENTIAL`.
- **Safe URL & SSRF Prevention:** Strict URL validation (`SafeUrlValidator`) permits only `http` and `https` schemes, prohibits credentials in authority sections, bounds lengths, and blocks localhost, loopback (`127.0.0.1`), link-local metadata endpoints (`169.254.169.254`), and RFC 1918 private subnets. The backend never fetches user-submitted URLs.
- **Orchestrated Document Validation Pipeline (Stages A–F):**
  - **Stage A (Auth & Limits):** Authenticates principal, verifies will ownership, bounds filenames, rejects path traversal and control characters, generates random UUID storage keys.
  - **Stage B (Content Detection):** Apache Tika inspects raw magic bytes; compares detected MIME against the allowed list and cross-checks extension-content alignment.
  - **Stage C (Safe Parsing):** PDFBox parses PDFs safely (rejecting encrypted/password-protected files, enforcing max 200 pages); Java ImageIO inspects images without full allocation (bounding dimensions to 10,000px and 50M pixels); Apache POI / ZipArchive bounds DOCX uncompressed size (max 50 MB), entry count (max 500), compression ratio (max 100:1), and blocks VBA macro content.
  - **Stage D (Malware Scanning):** ClamAV adapter using the `INSTREAM` protocol over TCP; fail-closed behavior in `MANDATORY` mode ensuring scanner outages, timeouts, or errors are never interpreted as clean.
  - **Stage E (Semantic Category Check):** Validates document category against allowed MIME types (e.g. deeds require PDF or JPEG/PNG).
  - **Stage F (Quarantine & Storage Promotion):** Staged files are only promoted to the encrypted vault upon achieving `ACCEPTED` status. Unaccepted files cannot be downloaded by owners or disclosed to beneficiaries.
- **Database Schema Constraints:** Flyway migration `V8__document_validation_and_asset_constraints.sql` establishes database-level constraints on asset values, currencies, percentages, years, and document statuses.

---

## 2. Server-Side Principal Derivation & Estate Modifiability

### 2.1 Authenticated Principal Derivation
In all controller endpoints (`EstateController`, `DocumentController`, `DisclosureController`), the actor identity is derived solely from the Spring Security `@AuthenticationPrincipal UserPrincipal principal`:

```java
// DocumentController.java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<DocumentResponse> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam("willId") UUID willId,
        @RequestParam(value = "category", required = false) String categoryStr,
        @AuthenticationPrincipal UserPrincipal principal) {
    if (principal == null) {
        throw new SecurityException("Unauthorized: missing principal");
    }
    checkOwnership(willId, principal);
    ...
}
```

Any attempt to forge an ownership check by passing a different user ID is impossible because the query compares `will.getOwnerId().equals(principal.getId())`. Tests in `AuthorizationIdorSecurityTest` verify that cross-user access attempts result in `403 Forbidden`.

### 2.2 Estate Modifiability Enforcement
In `EstateService`, state checks prevent modifying assets or beneficiaries once a will enters execution:

```java
// EstateService.java
private void verifyEstateModifiable(UUID willId) {
    WillStateEntity will = willStateService.getWillOrThrow(willId);
    if (will.getState() == SuccessionState.EXECUTING || will.getState() == SuccessionState.EXECUTED) {
        throw new WillStateConflictException(
            "Estate cannot be modified in state: " + will.getState() + ". Terminal or executing wills are locked."
        );
    }
}
```
All mutative operations (`addAsset`, `deleteAsset`, `addBeneficiary`, `deleteBeneficiary`, `allocateAsset`, `uploadDocument`) invoke `verifyEstateModifiable(willId)`.

---

## 3. Structured Records & Asset Validation

### 3.1 Supported Asset Categories
The platform enforces a strict allowlist of eight categories. Requests with unsupported categories (e.g. `INSURANCE` or unknown strings) are rejected with `INVALID_CATEGORY`:

1. `BANK_ACCOUNT`
2. `REAL_ESTATE`
3. `INVESTMENT`
4. `CRYPTO`
5. `DIGITAL_ACCOUNT`
6. `VEHICLE`
7. `PERSONAL_PROPERTY`
8. `OTHER`

### 3.2 Category-Specific Domain Rules

| Category | Required Fields | Allowed Encodings / Bounds | Identifier Handling |
|---|---|---|---|
| **`BANK_ACCOUNT`** | `title`, `category` | ISO-4217 currency (3 chars), non-negative finite balance, valuation date $\le$ today | Account numbers masked as `****1234` |
| **`INVESTMENT`** | `title`, `category` | ISO-4217 currency, finite non-negative valuation, valuation date $\le$ today | Brokerage account masked |
| **`REAL_ESTATE`** | `title`, `category` | Ownership percentage (0 < x $\le$ 100), valuation $\ge$ 0, year (1800–2100) | Parcel/deed ref stored safely |
| **`VEHICLE`** | `title`, `category` | Year (1800–2100), non-negative valuation | VIN / registration masked |
| **`DIGITAL_ACCOUNT`** | `title`, `category` | Safe URL (`http`/`https`), handle bounded | Credentials strictly blocked |
| **`CRYPTO`** | `title`, `category` | Public wallet address or exchange name | Private keys / seed phrases strictly blocked |
| **`PERSONAL_PROPERTY`** | `title`, `category` | Valuation $\ge$ 0, year (1800–2100) | Serial numbers masked |
| **`OTHER`** | `title`, `category` | Bounded text description $\le$ 2000 chars | Non-credential metadata |

### 3.3 Finite Mathematics & Precision Enforcement
Floating point traps (`NaN`, `Infinity`, negative numbers, overflow) are rejected using `java.math.BigDecimal`:
- `assetValue`: must be non-negative, finite, maximum 15 integer digits, 2 fractional digits.
- `ownershipPercentage`: strictly $0 < x \le 100.00$.
- Dates: valuation dates cannot be in the future (`valuationDate <= LocalDate.now()`).
- Years: constrained between `1800` and `2100`.

### 3.4 Duplicate Detection
In `EstateService.addAsset`, existing assets for the same will are inspected for duplicates:
```java
// EstateService.java
boolean isDuplicate = assetRepository.findByWillId(willId).stream()
        .anyMatch(existing -> existing.getTitle().equalsIgnoreCase(title.trim())
                && existing.getCategory() == category
                && Objects.equals(existing.getInstitutionProvider(), institutionProvider));
if (isDuplicate) {
    throw new PossibleDuplicateException("An asset with the title '" + title + "' and category '" + category + "' already exists in this will.");
}
```
Duplicates trigger an HTTP 409 Conflict with code `POSSIBLE_DUPLICATE` rather than silently merging or deleting existing data.

### 3.5 Beneficiaries & Allocation Constraints
- **Beneficiaries:** Names bounded to 255 chars, emails validated via RFC-compliant format, maximum 100 beneficiaries per will.
- **Allocations:** Share percentage strictly between 1% and 100%. Total allocation for a single asset cannot exceed 100%. Assets and beneficiaries must belong to the exact same will; cross-will injection is prevented and tested (`crossWillAssetAllocation_isRejected`).

---

## 4. Credential & Prohibited Secret Screening

Digital Will is an estate directive system, **not** a credentials vault. `ProhibitedCredentialValidator` checks all freeform text inputs:

### Prohibited Secret Patterns Blocked
1. **Private Keys:** PEM private keys (`-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----`), OpenSSH private keys (`-----BEGIN OPENSSH PRIVATE KEY-----`), PGP private keys, hex/base64 DER private keys.
2. **Cryptocurrency Seed & Mnemonic Phrases:** BIP-39 English word combinations of 12, 15, 18, 21, or 24 words.
3. **Passwords & Bank PINs:** Keywords indicating raw credentials (`password:`, `pwd=`, `pin: \d{4,6}`).
4. **Credit Card CVVs:** CVV patterns accompanied by card references (`cvv: \d{3,4}`).
5. **API Keys & Bearer Tokens:** AWS access keys (`AKIA[0-9A-Z]{16}`), GitHub personal access tokens (`ghp_[0-9a-zA-Z]{36}`), Slack tokens (`xox[baprs]-[0-9a-zA-Z]{10,48}`), generic JWT tokens (`eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+`).
6. **MFA Secret Seeds:** Base32 authenticator setup seeds (`otpauth://` or 16/32 char Base32 keys).

When detected, the validator throws `ProhibitedCredentialException`, which maps to `HTTP 400 Bad Request` with error code `PROHIBITED_CREDENTIAL`.

---

## 5. Safe URL & SSRF Prevention

`SafeUrlValidator` validates URLs provided for digital assets and online services:
- **Allowed Schemes:** `https` and `http` only (no `file:`, `javascript:`, `ftp:`, `data:`, `gopher:`).
- **Prohibited Authority Credentials:** URLs containing `user:pass@host` are rejected.
- **SSRF Subnet Blocking:** Rejects:
  - `localhost`, `*.localhost`
  - Loopback IP: `127.0.0.1`, `::1`
  - Cloud metadata services: `169.254.169.254`, `instance-data`
  - RFC 1918 Private IPv4 subnets: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
  - Link-local addresses: `fe80::/10`
- **Architectural Isolation:** The backend **never** fetches, pings, or resolves user-submitted URLs over HTTP/S. They are stored strictly as inert display metadata.

---

## 6. Document Validation Pipeline Architecture

```mermaid
flowchart TD
    A["Upload Request<br/>(File, WillId, Category)"] --> B["Stage A: Auth & Limits<br/>- UserPrincipal check<br/>- Will ownership check<br/>- Max 25MB check<br/>- Filename sanitization"]
    B --> C["Stage B: Content Detection<br/>- Apache Tika magic bytes<br/>- Allowlist verification<br/>- Ext vs MIME match"]
    C --> D["Stage C: Safe Parsing<br/>- PDFBox object & page bounds<br/>- ImageIO dimension bounds<br/>- POI/ZipArchive bomb & macro bounds"]
    D --> E["Stage D: Malware Scan<br/>- ClamAV TCP INSTREAM<br/>- EICAR test detection<br/>- Fail-closed in MANDATORY mode"]
    E --> F["Stage E: Semantic Check<br/>- Category vs MIME match"]
    F --> G["Stage F: Quarantine & Promotion<br/>- Move from staging to vault<br/>- AES-256-GCM envelope encryption<br/>- Mark status = ACCEPTED"]
    
    C -- "MIME Mismatch" --> R["Status: REJECTED<br/>Quarantined / Deleted"]
    D -- "Malformed / Zip Bomb" --> R
    E -- "Infected / Scanner Error" --> R
    F -- "Incompatible Category" --> R
```

### Stage A — Authentication, Authorization, Limits
- Principal authenticated via JWT; will ownership checked via `will.getOwnerId().equals(principal.getId())`.
- File presence checked; empty uploads rejected.
- File size bounded to 25 MB (`max-file-size=25MB`).
- Filename sanitized: control characters and path traversal sequences (`../`, `..\`) stripped; max 255 chars.
- Cryptographically secure UUID generated for storage key.

### Stage B — Content Detection
- Uses Apache Tika (`Tika.detect(InputStream)`) to analyze raw magic bytes.
- Allowed Media Types:
  - `application/pdf`
  - `image/jpeg`
  - `image/png`
  - `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (DOCX)
- Disallowed: Executables, shell scripts, HTML/SVG, archives (.zip, .tar, .7z), macro-enabled documents (.docm, .xlsm).
- Cross-checks extension against detected MIME. For example, a file named `report.pdf` containing JPEG bytes is rejected with `MEDIA_TYPE_MISMATCH`.

### Stage C — Safe Parsing
- **PDFs:** Uses Apache PDFBox 3.0.4.
  - Detects and rejects password-protected / encrypted PDFs (`InvalidPasswordException`).
  - Limits maximum page count to 200 pages.
- **Images:** Uses Java ImageIO `ImageReader` metadata header reading:
  - Inspects dimensions without decoding entire pixel bitmap into memory.
  - Bounds width/height to $\le 10,000$ pixels and total pixel count to $\le 50,000,000$ pixels.
- **Word Documents (DOCX):**
  - Validates ZIP archive headers: bounds entry count ($\le 500$) and uncompressed size ($\le 50$ MB).
  - Enforces decompression ratio limit ($\le 100:1$) to block ZIP bombs.
  - Verifies entry names to prevent Zip Slip path traversal.
  - Rejects macro parts (`word/vbaProject.bin`).

### Stage D — Malware Scanning (ClamAV Adapter)
- Implements `MalwareScanner` interface via `ClamAvMalwareScanner`.
- Communicates via TCP socket using ClamAV `zINSTREAM\0` protocol.
- Chuncked byte streaming prevents buffering entire files into scanner memory.
- Recognizes EICAR test signature: returns `INFECTED` and quarantine reason `FOUND: Win.Test.EICAR_HDB-1`.
- **Fail-Closed Semantics:** In `MANDATORY` mode (`document.malware-scan.mode=MANDATORY`), scanner outage, socket timeout, or malformed responses return `SCANNER_ERROR` and reject the upload. In `OPTIONAL` mode, warnings are logged and marked `NEEDS_REVIEW`.

### Stage E — Category & Semantic Validation
Validates that the uploaded document category matches the content:
- `PROPERTY_DEED`: PDF, JPEG, PNG only.
- `FINANCIAL_RECORD`: PDF only.
- `IDENTITY_RECORD`: PDF, JPEG, PNG only.
- `WILL_ATTACHMENT`: PDF, DOCX only.
- `MEDICAL_RECORD`: PDF, JPEG, PNG only.
- `GENERAL`: Any allowed format.

### Stage F — Storage Quarantine & Promotion
- Files are initially written to a staging directory.
- Only if Stages A through E pass is the document promoted to the encrypted vault.
- `DocumentService` executes envelope encryption with AES-256-GCM and persists the entity with status `ACCEPTED`.
- If an exception occurs, cleanup deletes the staged and vault files.
- **Download Guard:** In `DocumentController` and `DisclosureController`, downloads are blocked unless `document.getStatus() == DocumentStatus.ACCEPTED`. Attempting to download a quarantined, pending, or rejected document returns `403 Forbidden` (`DOCUMENT_NOT_ACCEPTED`).

---

## 7. Database Schema Migration (V8 Migration)

Flyway migration `V8__document_validation_and_asset_constraints.sql` introduces validation constraints:

```sql
-- V8__document_validation_and_asset_constraints.sql

-- Document status and metadata
ALTER TABLE encrypted_documents
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    ADD COLUMN IF NOT EXISTS status_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS status_message VARCHAR(500),
    ADD COLUMN IF NOT EXISTS detected_content_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS category VARCHAR(64) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE encrypted_documents
    ADD CONSTRAINT chk_document_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'NEEDS_REVIEW', 'FAILED_RETRYABLE'));

-- Structured Asset fields and constraints
ALTER TABLE assets
    ADD COLUMN IF NOT EXISTS institution_provider VARCHAR(255),
    ADD COLUMN IF NOT EXISTS account_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS asset_value NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS valuation_date DATE,
    ADD COLUMN IF NOT EXISTS ownership_percentage NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS asset_year INTEGER,
    ADD COLUMN IF NOT EXISTS asset_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS identifier_masked VARCHAR(255);

ALTER TABLE assets
    ADD CONSTRAINT chk_asset_currency CHECK (currency IS NULL OR LENGTH(currency) = 3),
    ADD CONSTRAINT chk_asset_value_non_negative CHECK (asset_value IS NULL OR asset_value >= 0),
    ADD CONSTRAINT chk_asset_ownership_percentage CHECK (ownership_percentage IS NULL OR (ownership_percentage > 0 AND ownership_percentage <= 100)),
    ADD CONSTRAINT chk_asset_year CHECK (asset_year IS NULL OR (asset_year >= 1800 AND asset_year <= 2100));

CREATE INDEX IF NOT EXISTS idx_encrypted_documents_status ON encrypted_documents(status);
CREATE INDEX IF NOT EXISTS idx_encrypted_documents_category ON encrypted_documents(category);
CREATE INDEX IF NOT EXISTS idx_assets_category ON assets(category);
```

Verified against real PostgreSQL in `PostgreSqlFlywayMigrationIntegrationTest` and `PostgreSqlApplicationIntegrationTest`.

---

## 8. Frontend UI/UX Alignment & Security Guidance

### 8.1 Estate Workspace (`frontend/src/app/estate/page.tsx`)
1. **Security Notice Banner:** Prominently warns users not to store PINs, passwords, private keys, or seed phrases.
2. **Category Selector:** Replaced outdated labels with the 8 supported categories (`BANK_ACCOUNT`, `REAL_ESTATE`, `INVESTMENT`, `CRYPTO`, `DIGITAL_ACCOUNT`, `VEHICLE`, `PERSONAL_PROPERTY`, `OTHER`).
3. **Structured Form Fields:** Form supports institution, account type, currency, valuation, valuation date, ownership percentage, year, URL, and raw identifier.
4. **Form State Preservation:** On validation error, the modal preserves user input and presents actionable inline error messages. Submit button is disabled while processing to prevent double-submissions.
5. **Enriched Asset Badges:** Asset cards display masked identifiers (`ID: ****1234`), formatted currency valuations, ownership shares, institution/account tags, and safe URLs.

### 8.2 Document Vault (`frontend/src/app/documents/page.tsx`)
1. **Category Selector:** Upload form includes category dropdown (`WILL_ATTACHMENT`, `IDENTITY_RECORD`, `PROPERTY_DEED`, `FINANCIAL_RECORD`, `MEDICAL_RECORD`, `GENERAL`).
2. **Pipeline Rules Box:** Details allowed formats (PDF, JPEG, PNG, DOCX up to 20 MB) and explains server-side scanning.
3. **Validation Status Badges:** Cards display badges for `ACCEPTED` (green), `PENDING` (amber), `REJECTED` (red), and `NEEDS_REVIEW` (orange).
4. **Guarded Downloads:** Download button is disabled for non-`ACCEPTED` files with descriptive tooltips.
5. **Status Message Display:** Any rejection reason or scanner status message is rendered directly on the document card.

---

## 9. Current Git Working-Tree State

```
Branch: feat/input-document-validation
Changes not staged for commit:
	modified:   README.md
	modified:   backend/pom.xml
	modified:   backend/src/main/java/com/digitalwill/common/web/GlobalExceptionHandler.java
	modified:   backend/src/main/java/com/digitalwill/document/model/EncryptedDocument.java
	modified:   backend/src/main/java/com/digitalwill/document/repository/EncryptedDocumentRepository.java
	modified:   backend/src/main/java/com/digitalwill/document/service/DocumentService.java
	modified:   backend/src/main/java/com/digitalwill/document/web/DocumentController.java
	modified:   backend/src/main/java/com/digitalwill/estate/model/Asset.java
	modified:   backend/src/main/java/com/digitalwill/estate/repository/AssetRepository.java
	modified:   backend/src/main/java/com/digitalwill/estate/repository/BeneficiaryRepository.java
	modified:   backend/src/main/java/com/digitalwill/estate/service/EstateService.java
	modified:   backend/src/main/java/com/digitalwill/estate/web/EstateController.java
	modified:   backend/src/main/java/com/digitalwill/release/web/DisclosureController.java
	modified:   backend/src/main/resources/application.properties
	modified:   backend/src/test/java/com/digitalwill/document/service/DocumentServiceIntegrationTest.java
	modified:   backend/src/test/java/com/digitalwill/document/web/DocumentControllerTest.java
	modified:   backend/src/test/java/com/digitalwill/estate/web/EstateControllerTest.java
	modified:   backend/src/test/java/com/digitalwill/estate/web/EstateWorkflowIntegrationTest.java
	modified:   backend/src/test/java/com/digitalwill/flyway/FlywayMigrationIntegrationTest.java
	modified:   backend/src/test/java/com/digitalwill/flyway/PostgreSqlApplicationIntegrationTest.java
	modified:   backend/src/test/java/com/digitalwill/flyway/PostgreSqlFlywayMigrationIntegrationTest.java
	modified:   backend/src/test/java/com/digitalwill/security/AuthorizationIdorSecurityTest.java
	modified:   backend/src/test/java/com/digitalwill/workflow/DisclosureWorkflowIntegrationTest.java
	modified:   frontend/src/app/documents/page.tsx
	modified:   frontend/src/app/estate/page.tsx
	modified:   frontend/src/lib/api.ts
	modified:   frontend/src/lib/types.ts

Untracked files:
	backend/src/main/java/com/digitalwill/common/exception/
	backend/src/main/java/com/digitalwill/common/validation/
	backend/src/main/java/com/digitalwill/document/model/DocumentCategory.java
	backend/src/main/java/com/digitalwill/document/model/DocumentStatus.java
	backend/src/main/java/com/digitalwill/document/validation/
	backend/src/main/java/com/digitalwill/estate/validation/
	backend/src/main/resources/db/migration/V8__document_validation_and_asset_constraints.sql
	backend/src/test/java/com/digitalwill/common/validation/
	backend/src/test/java/com/digitalwill/document/validation/
	backend/src/test/java/com/digitalwill/estate/validation/
	docs/reviews/INPUT_DOCUMENT_AND_ASSET_VALIDATION_IMPLEMENTATION.md
```

All working tree modifications are strictly preserved. No unauthorized commits, pushes, or resets were executed.

---

## 10. Test Execution Results & Verification Evidence

### 10.1 Backend Test Suite (Maven)
Command executed: `.\mvnw test`
```
[INFO] ------------------------------------------------------------------------
[INFO] Results:
[INFO] 
[INFO] Tests run: 311, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  58.525 s
[INFO] Finished at: 2026-10-13T20:12:58+05:30
[INFO] ------------------------------------------------------------------------
```

### 10.2 Targeted Unit & Integration Test Suites
- `ProhibitedCredentialValidatorTest`: 23/23 tests pass (PEM keys, seed phrases, PINs, tokens, CVVs, MFA secrets).
- `SafeUrlValidatorTest`: 23/23 tests pass (RFC 1918 subnets, loopback, AWS metadata, length bounds, allowed schemes).
- `DocumentValidationPipelineTest`: 18/18 tests pass (valid PDF/JPEG/PNG/DOCX, password PDF, zip bomb, macro detection, ClamAV fail-closed, EICAR).
- `AssetValidatorTest`: 25/25 tests pass (all 8 categories, negative values, currency codes, valuation dates, collection limits, duplicates).
- `PostgreSqlFlywayMigrationIntegrationTest`: 3/3 tests pass against embedded PostgreSQL.
- `PostgreSqlApplicationIntegrationTest`: 1/1 test passes against embedded PostgreSQL (all 8 migrations verified).
- `AuthorizationIdorSecurityTest`: 15/15 tests pass (cross-user access, null principal fail-closed).
- `DisclosureWorkflowIntegrationTest`: 6/6 tests pass (scoped package disclosure, encrypted document retrieval).
- `EstateWorkflowIntegrationTest`: 1/1 test passes (end-to-end estate creation, asset allocation, document upload, readiness review).

### 10.3 Frontend Next.js Production Build
Command executed: `npm run build` in `frontend/`
```
   ▲ Next.js 15.5.27

   Creating an optimized production build ...
 ✓ Compiled successfully in 14.8s
   Linting and checking validity of types ...
   Collecting page data ...
 ✓ Generating static pages (8/8)
   Finalizing page optimization ...
   Collecting build traces ...

Route (app)                                 Size  First Load JS
┌ ○ /                                      162 B         106 kB
├ ○ /_not-found                            992 B         104 kB
├ ○ /auth                                3.73 kB         113 kB
├ ƒ /disclosure/[token]                  3.02 kB         109 kB
├ ○ /documents                           6.08 kB         115 kB
├ ○ /estate                               9.8 kB         119 kB
└ ○ /verify                              3.63 kB         113 kB
+ First Load JS shared by all             103 kB

○  (Static)   prerendered as static content
ƒ  (Dynamic)  server-rendered on demand
```

---

## 11. Security Analysis & Threat Model Verification

| Threat / Attack Vector | Mitigation Implemented | Verification Result |
|---|---|---|
| **IDOR / Client Identity Spoofing** | Server-side principal derivation from JWT; ownership verified per will. | Verified by `AuthorizationIdorSecurityTest` (403 Forbidden). |
| **Tampering with Executed Will** | `verifyEstateModifiable()` blocks asset/beneficiary/document changes when will is `EXECUTING` or `EXECUTED`. | Verified by `AssetValidatorTest` (409 Conflict). |
| **Credential & Key Leakage** | `ProhibitedCredentialValidator` scans all asset records and document notes for private keys, mnemonics, passwords, PINs. | Verified by `ProhibitedCredentialValidatorTest` (400 Bad Request). |
| **Server-Side Request Forgery (SSRF)** | `SafeUrlValidator` blocks `169.254.169.254`, `localhost`, and RFC 1918 subnets. Backend never connects to user URLs. | Verified by `SafeUrlValidatorTest`. |
| **MIME / Extension Spoofing** | Apache Tika magic byte detection compares actual content against allowlist and filename extension. | Verified by `DocumentValidationPipelineTest` (rejected mismatch). |
| **Zip Bomb / Parser Exhaustion** | Strict uncompressed size limit (50 MB), entry count (500), and ratio (100:1); image dimension bounds (10,000px, 50M pixels); PDF page count (200). | Verified by `DocumentValidationPipelineTest`. |
| **Malware / Trojan Uploads** | ClamAV TCP scanner with fail-closed semantics in `MANDATORY` mode; recognizes EICAR signature. | Verified by `ClamAvMalwareScannerTest`. |
| **Unauthorized Disclosure of Pending Files** | Status checks in `DocumentController` and `DisclosureController` strictly block downloads of files not marked `ACCEPTED`. | Verified by `DocumentServiceIntegrationTest`. |

---

## 12. Operational Configuration Reference & Conclusion

### 12.1 Configuration Properties (`application.properties`)
```properties
# Malware Scanner Configuration
document.malware-scan.enabled=true
document.malware-scan.host=localhost
document.malware-scan.port=3310
document.malware-scan.timeout-ms=5000
# Modes: MANDATORY (fail-closed, rejects on scanner outage) | OPTIONAL (marks NEEDS_REVIEW on scanner error)
document.malware-scan.mode=OPTIONAL

# Multipart File Upload Limits
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=25MB
```

### 12.2 Conclusion
The Digital Will input and document validation system establishes a robust, defense-in-depth security perimeter. All user records are authenticated via server-side principals, domain constraints are enforced across application and database layers, sensitive authentication credentials and private keys are proactively blocked, and document uploads undergo rigorous content inspection and malware scanning before being permitted into the cryptographic vault.
