# Security Policy & Architecture — Digital Will

## 1. Overview & Security Model

Digital Will is a secure digital estate and succession management platform. Its security architecture enforces strict server-authoritative state transitions, envelope encryption for sensitive documents, partitioned disclosure access, and tamper-evident audit logging.

The system is designed with a **fail-closed** security posture: any failure in cryptographic verification, access token validation, or state transition guards halts the operation immediately without revealing partial or unauthenticated data.

---

## 2. Cryptographic Architecture

### 2.1 AES-256-GCM Envelope Encryption
- **Document Payloads:** Encrypted using standard AES-256-GCM (`AES/GCM/NoPadding`) with a 128-bit authentication tag.
- **Initialization Vectors (IVs):** Every encryption operation generates a unique, cryptographically strong 96-bit (12 bytes) IV using `SecureRandom`. IVs are never reused.
- **Key Hierarchy:**
  - **Master Key (Key Encryption Key / KEK):** 256-bit key configured via secure environment configuration (`DIGITALWILL_CRYPTO_MASTER_KEY`). For MVP, the master key is managed via environment variables / secret manager; production deployments can bind this to Cloud KMS (AWS KMS, GCP KMS, or HashiCorp Vault).
  - **Data Encryption Key (DEK):** A distinct 256-bit AES key is generated ephemerally for each document.
  - **Wrapped DEK:** The DEK is encrypted under the KEK using AES-256-GCM with its own distinct IV. The encrypted DEK and IV are stored alongside document metadata; plaintext DEKs are discarded immediately after processing.
- **Integrity & Fail-Closed Decryption:**
  - Decryption authenticates the GCM authentication tag before returning plaintext.
  - Tampering with ciphertext, authentication tags, or wrapped DEKs immediately triggers a fail-closed `DecryptionFailedException`.
  - Zero unauthenticated plaintext is written or exposed.
  - Cryptographic keys, raw tokens, and document plaintexts are strictly excluded from logs, error messages, and audit records.

---

## 3. Authentication & Authorization Boundaries

### 3.1 User Authentication & Token Security
- **Credential Storage:** User passwords are encrypted with standard BCrypt (`strength = 12`) password hashing using secure salts. Plaintext passwords are never logged or stored.
- **Bearer Tokens:** Session tokens are cryptographically strong random tokens generated with `SecureRandom` (32 bytes / 256 bits).
- **Hashed Session Tokens:** Raw Bearer tokens are returned exclusively to the authenticating client. The database stores only SHA-256 digests (`token_hash`) in the `user_auth_tokens` table.
- **Token Invalidation:** Logout immediately marks tokens revoked, and stale tokens can be expired via background eviction or revocation.

### 3.2 Server-Enforced User-to-Will Ownership Boundary
- **Principal Derivation:** All protected estate, asset, beneficiary, allocation, contact, and document APIs strictly derive the calling user from the authenticated `UserPrincipal` extracted by `TokenAuthenticationFilter`.
- **Zero Frontend Trust:** Client-supplied owner IDs are never trusted. Attempting to query, modify, or delete a will or sub-resource belonging to another user results in an immediate fail-closed `403 Forbidden`.
- **Single-Will Invariant:** MVP guarantees exactly one Digital Will per user, enforced at both the relational level (`uq_wills_owner_user_id`) and service level. Subsequent creation attempts fail with `409 Conflict`.
- **Pre-Decryption Authorization:** Documents cannot be decrypted or streamed without prior ownership validation on the parent will and document record.

### 3.3 Document Storage
- **MVP Implementation:** Encrypted document blobs are stored locally in a designated secure storage directory.
- **Path Sanitization:** Storage identifiers are strictly validated to prevent directory traversal (`..`, `:`, `/`, `\` characters are rejected).
- **Leakage Prevention:** File system paths and operating system internals are sanitized and never leaked to API clients.

### 3.4 Partitioned Controlled Disclosure
- **Information Isolation:** Beneficiaries do not receive general estate inventories. Each beneficiary receives access only to assets and documents specifically allocated to them.
- **Document Access Enforcement:** The server explicitly verifies that requested document IDs reside within the beneficiary's authorized disclosure package. Cross-beneficiary document access attempts are rejected with `403 Forbidden`.
- **Disclosure Tokens:**
  - 256-bit cryptographically secure random tokens.
  - Hashed at rest using SHA-256; raw tokens are never persisted.
  - Single-use and replay-protected: Once accessed, tokens are atomically transitioned to consumed/accessed.
  - Time-bounded: Expired tokens are rejected with `410 Gone`.
  - Revocable: If the estate owner performs an activity reset, pending tokens are invalidated.

---

## 4. State Engine & Concurrency Safety

### 4.1 Server-Authoritative State Machine
- State transitions are strictly server-authoritative; client applications cannot set or modify succession states directly.
- All transitions execute through guarded conditional database queries (e.g. `UPDATE wills SET state = :newState WHERE id = :id AND state = :expectedState`).
- Multi-worker claim races result in exactly one winner (1 row updated); losing workers safely receive 0 rows updated and abort without side effects.

### 4.2 Release Crash Recovery
- If a release worker halts or crashes mid-execution (`EXECUTING` state), a recovery process transitions the will back to `RELEASE_PENDING` once the execution lease expires.
- Per-item execution tracking (`release_execution_items`) ensures already-completed disclosures are skipped during subsequent retries (effectively-once / idempotent item delivery).

---

## 5. Tamper-Evident Audit Logging

- **Hash Chaining:** Each audit entry is linked to the previous entry via SHA-256 hashing:
  $$\text{hash}_N = \text{SHA-256}(\text{previous\_hash}_N \,\|\, \text{sequence\_number} \,\|\, \text{will\_id} \,\|\, \text{timestamp} \,\|\, \text{action} \,\|\, \text{status} \,\|\, \text{payload})$$
- **Genesis Record:** The first entry for any will has sequence number 1 and a well-defined zero-hash as previous hash.
- **Tamper Detection:** `AuditLogService.verifyAuditLogIntegrity(willId)` verifies unbroken chains and flags any modification, deletion (gap), or reordering.
- **Transactional Rollback:** In critical flows (e.g. state transitions and release execution), audit log recording participates in the active transaction, ensuring fail-closed rollback if audit persistence fails.

---

## 6. Background Jobs & Timing Attack Defenses

- Scheduled job endpoints (`/internal/jobs/**`) are protected by a pre-shared internal secret header (`X-Internal-Job-Secret`).
- Comparison of incoming job secrets uses constant-time byte comparison (`MessageDigest.isEqual`) to eliminate timing side-channel vulnerabilities.

---

## 7. Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it privately:
- Do NOT open a public GitHub issue.
- Send details and reproduction steps to the security team or project maintainer.
