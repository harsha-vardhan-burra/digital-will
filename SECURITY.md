# Security Policy & Architecture — Digital Will

## 1. Overview & Security Model

Digital Will is a secure digital estate and succession management platform. Its security architecture enforces strict server-authoritative state transitions, envelope encryption for sensitive documents, partitioned disclosure access, tamper-evident audit logging, and robust abuse protection.

The system is designed with a **fail-closed** security posture: any failure in cryptographic verification, access token validation, rate limit evaluation, or state transition guards halts the operation immediately without revealing partial or unauthenticated data.

During Phase 4 (Security Verification & Production Hardening), the platform underwent comprehensive adversarial abuse testing across 26 distinct attack scenarios documented in [`docs/threat-model-phase4.md`](file:///C:/Users/harsh/Projects/digital-will/docs/threat-model-phase4.md). The backend test suite was expanded from 133 to 199 automated tests, with 66 dedicated adversarial security tests verifying all security boundaries.

---

## 2. Cryptographic Architecture

### 2.1 AES-256-GCM Envelope Encryption
- **Document Payloads:** Encrypted using standard AES-256-GCM (`AES/GCM/NoPadding`) with a 128-bit authentication tag.
- **Initialization Vectors (IVs):** Every encryption operation generates a unique, cryptographically strong 96-bit (12 bytes) IV using `SecureRandom`. IVs are never reused (CPA security verified).
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
- **Client Session Storage (MVP Limitation):** MVP authentication stores the bearer session token in browser `localStorage`. This is a known deployment limitation because a token accessible to JavaScript can be exposed by an XSS vulnerability. A production deployment should prefer a hardened HttpOnly, Secure, SameSite cookie-based session mechanism or an equivalent security architecture.

### 3.2 Server-Enforced User-to-Will Ownership Boundary
- **Principal Derivation:** All protected estate, asset, beneficiary, allocation, contact, and document APIs strictly derive the calling user from the authenticated `UserPrincipal` extracted by `TokenAuthenticationFilter`.
- **Zero Frontend Trust:** Client-supplied owner IDs are never trusted. Attempting to query, modify, or delete a will or sub-resource belonging to another user results in an immediate fail-closed `403 Forbidden`.
- **Fail-Closed Controller Guards:** Controllers explicitly verify that `principal != null` prior to domain processing, throwing `SecurityException("Authentication required")` if unauthenticated.
- **Single-Will Invariant:** MVP guarantees exactly one Digital Will per user, enforced at both the relational level (`uq_wills_owner_user_id`) and service layer. Subsequent creation attempts fail with `409 Conflict`.
- **Pre-Decryption Authorization:** Documents cannot be decrypted or streamed without prior ownership validation on the parent will and document record.
- **Download Header Sanitization:** File download endpoints sanitize document filenames in `Content-Disposition` headers to prevent HTTP response splitting and header injection.

### 3.3 Document Storage & Path Traversal Prevention
- **MVP Implementation:** Encrypted document blobs are stored locally in a designated secure storage directory.
- **Path Sanitization:** Storage identifiers are strictly validated to prevent directory traversal (`..`, `:`, `/`, `\` characters and absolute path patterns are rejected with `SecurityException`).
- **Leakage Prevention:** File system paths and operating system internals are sanitized and never leaked to API clients.

### 3.4 Partitioned Controlled Disclosure
- **Information Isolation:** Beneficiaries do not receive general estate inventories. Each beneficiary receives access only to assets and documents specifically allocated to them.
- **Document Access Enforcement:** The server explicitly verifies that requested document IDs reside within the beneficiary's authorized disclosure package. Cross-beneficiary document access attempts are rejected with `403 Forbidden`.
- **Pre-Execution Protection:** Disclosures cannot be accessed while a will is still in pre-execution states (`ACTIVE`, `WARNING`, `VERIFIED`, `RELEASE_PENDING`), returning `409 Conflict`.
- **Disclosure Tokens:**
  - 256-bit cryptographically secure random tokens.
  - Hashed at rest using SHA-256; raw tokens are never persisted.
  - Single-use and replay-protected: Once accessed, tokens are atomically transitioned to consumed/accessed. Subsequent consumption attempts fail with `409 Conflict`.
  - Time-bounded: Expired tokens are rejected with `410 Gone`.
  - Revocable: If the estate owner performs an activity reset, pending tokens are invalidated.

---

## 4. State Engine & Concurrency Safety

### 4.1 Server-Authoritative State Machine
- State transitions are strictly server-authoritative; client applications cannot set or modify succession states directly.
- All transitions execute through guarded conditional database updates (e.g. `UPDATE wills SET state = :newState WHERE id = :id AND state = :expectedState`).
- Multi-worker claim races result in exactly one winner (1 row updated); losing workers safely receive 0 rows updated and abort without side effects.
- Illegal transitions (e.g. skipping from `ACTIVE` to `EXECUTED` or transitioning from terminal `EXECUTED`) are strictly rejected by the `TransitionRegistry`.

### 4.2 Release Crash Recovery
- If a release worker halts or crashes mid-execution (`EXECUTING` state), a recovery process transitions the will back to `RELEASE_PENDING` once the execution lease expires (default 15 minutes).
- Per-item execution tracking (`release_execution_items`) ensures already-completed disclosures are skipped during subsequent retries (effectively-once / idempotent item delivery).
- Maximum retries are bounded (3 attempts); exceeding retries marks execution `FAILED_PERMANENT` for manual administrative intervention without infinite loops.

---

## 5. Tamper-Evident Audit Logging

- **Hash Chaining:** Each audit entry is linked to the previous entry via SHA-256 hashing:
  $$\text{hash}_N = \text{SHA-256}(\text{previous\_hash}_N \,\|\, \text{sequence\_number} \,\|\, \text{will\_id} \,\|\, \text{timestamp} \,\|\, \text{action} \,\|\, \text{status} \,\|\, \text{payload})$$
- **Genesis Record:** The first entry for any will has sequence number 1 and a well-defined zero-hash as previous hash (`0000...0000`).
- **Tamper Detection:** `AuditLogService.verifyGlobalIntegrity()` verifies unbroken chains and flags any payload modification, entry hash tampering, previous hash tampering, or sequence gaps (deletions).
- **Concurrency & Monotonicity:** Synchronized append transactions ensure that linear sequence numbering and SHA-256 hash chaining remain unbroken under multi-worker concurrency.
- **Transactional Rollback:** In critical security flows, failure to record an audit entry throws `AuditPersistenceException`, triggering automatic transactional rollback of the calling operation.

---

## 6. Rate Limiting & Abuse Prevention (Workstream 7)

To defend against automated credential stuffing, token brute-forcing, and resource exhaustion, an in-memory sliding-window rate limiter intercepts incoming unauthenticated requests:
- **Rate-Limited Endpoints:**
  - `/api/auth/login` (POST): 5 requests per 60-second sliding window per client IP.
  - `/api/auth/register` (POST): 5 requests per 60-second sliding window per client IP.
  - `/api/verification/confirm` (POST): 10 requests per 60-second sliding window per client IP.
  - `/api/disclosure/**` (GET/POST): 10 requests per 60-second sliding window per client IP.
- **Response On Threshold Breach:** Returns HTTP `429 Too Many Requests` with a standard `Retry-After: <seconds>` header and structured JSON error payload.
- **Cooldown & Reset:** Expired request timestamps are continuously purged from sliding windows, automatically restoring access once the cooldown expires.

---

## 7. Production Security Configuration (Workstream 8)

- **Strict CORS:**
  - Replaces wildcard origins (`*`) with configurable, explicit origin whitelists (`app.cors.allowed-origins`).
  - Restricts allowed HTTP methods and headers to required application verbs.
  - Exposes only `Retry-After` and `Content-Disposition` headers to client applications.
- **Production Security Headers:**
  - `Content-Security-Policy`: `default-src 'self'; frame-ancestors 'none'; object-src 'none'`
  - `X-Frame-Options`: `DENY`
  - `X-Content-Type-Options`: `nosniff`
  - `Strict-Transport-Security` (HSTS): `max-age=31536000; includeSubDomains`
  - `Referrer-Policy`: `strict-origin-when-cross-origin`
  - `Permissions-Policy`: `geolocation=(), microphone=(), camera=(), payment=()`
- **Actuator Endpoint Hardening:**
  - Restricted to `/actuator/health` and `/actuator/info`.
  - Detailed health checks and sensitive administrative endpoints (`/env`, `/beans`, `/heapdump`) are strictly excluded.
- **Information Leakage Prevention:**
  - `server.error.include-stacktrace=never` and `server.error.include-message=never` ensure that internal exceptions and class hierarchies are never exposed to clients.
- **Test Route Exclusion:**
  - Development/test routes (`/test/errors/**`) are excluded from production deployments via `security.test-routes.enabled=false`.
- **Timing Attack Resistance on Internal Jobs:**
  - Scheduled worker endpoints (`/internal/jobs/**`) require `X-Internal-Job-Secret` verified via constant-time byte comparison (`MessageDigest.isEqual`).

---

## 8. Reporting a Vulnerability

If you discover a security vulnerability in this project, please report it privately:
- Do NOT open a public GitHub issue.
- Send details and reproduction steps to the security team or project maintainer.
