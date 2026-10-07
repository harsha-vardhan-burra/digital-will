# Digital Will — Phase 4 Security Threat Model & Attack Matrix

## 1. Executive Summary

Phase 4 establishes rigorous security verification and production hardening for Digital Will. Rather than introducing speculative architectural complexity, Phase 4 evaluates whether the existing security architecture withstands realistic misuse, malicious abuse, concurrent worker contention, and production deployment conditions.

The guiding methodology is evidence-driven:
$$\text{Security Claim} \longrightarrow \text{Adversarial Abuse Case} \longrightarrow \text{Automated Test} \longrightarrow \text{Observed Behavior} \longrightarrow \text{Minimal Fix} \longrightarrow \text{Regression Test} \longrightarrow \text{Documented Limitation}$$

---

## 2. Core Assets & Valuation

| Asset | Sensitivity | Integrity Impact | Confidentiality Impact | Storage / Location |
|---|---|---|---|---|
| **User Credentials** | High | High (unauthorized login) | High (credential theft) | `users.password_hash` (BCrypt cost=12) |
| **Authentication Session Tokens** | High | High (session hijacking) | High (identity impersonation) | Memory / DB `user_auth_tokens.token_hash` (SHA-256) |
| **Digital Will Entities & State** | Critical | Critical (premature release) | Medium | PostgreSQL `wills` table |
| **Asset & Allocation Details** | High | High (allocation tampering) | High (unauthorized estate visibility) | PostgreSQL `assets`, `asset_allocations` |
| **Beneficiary Information** | High | High (routing to wrong party) | High (PII disclosure) | PostgreSQL `beneficiaries` |
| **Trusted Contact Identifiers** | High | High (unauthorized verification) | Medium (contact disclosure) | PostgreSQL `trusted_contacts`, `will_contacts` |
| **Verification Tokens** | High | High (forged quorum confirmation) | High (cycle compromise) | DB `verification_requests.token_hash` (SHA-256) |
| **Disclosure Tokens** | Critical | High (token replay / theft) | Critical (unauthorized document access) | DB `disclosure_tokens.token_hash` (SHA-256) |
| **Plaintext Documents** | Critical | Critical (tampered will) | Critical (unauthorized will leakage) | Never stored plaintext; ephemeral memory only |
| **Data Encryption Keys (DEKs)** | Critical | Critical (decrypt all docs) | Critical (unauthorized disclosure) | Ephemeral in memory during crypto operations |
| **Wrapped DEKs** | High | High (corruption fails decrypt) | High (protected by KEK) | PostgreSQL `encrypted_documents.encrypted_dek` |
| **Master Key (KEK)** | Critical | Critical (decrypts all DEKs) | Critical (full vault breach) | Environment variable `APP_CRYPTO_MASTER_KEY` |
| **Encrypted Document Blobs** | High | High (tampering detected by GCM) | High (protected by AES-256-GCM) | Filesystem storage directory (`*.enc`) |
| **Audit Log Hash Chain** | Critical | Critical (undetected tampering) | Medium (audit trail leakage) | PostgreSQL `audit_logs` (SHA-256 chained) |
| **Internal Job Secret** | High | High (unauthorized trigger) | High (job denial of service) | Environment variable `APP_JOBS_SECRET` |

---

## 3. Trust Boundaries

```
[ Unauthenticated Internet / Client Browser ]
                     │
         [Trust Boundary 1: Transport & Network]
                     │ (TLS, CORS, Security Headers)
                     ▼
             [ Next.js Frontend ]
                     │
         [Trust Boundary 2: Client-to-API Perimeter]
                     │ (Bearer Token, Rate Limiting, Pre-flight CORS)
                     ▼
         [ Spring Boot API Controller Layer ]
                     │
         [Trust Boundary 3: Authentication & Authorization]
                     │ (TokenAuthenticationFilter -> UserPrincipal -> Ownership Check)
                     ▼
         [ Core Domain Services & State Engine ]
                     │
         [Trust Boundary 4: Cryptographic & Storage Boundary]
                     │ (Envelope Encryption, Path Traversal Sanitization, Pre-Decryption Auth)
                     ▼
[ Local Filesystem Storage ]          [ PostgreSQL Database ]
 (Encrypted Blobs *.enc)             (Relational Data, Hashes, Chains)
```

### Trust Boundary Analysis
1. **Trust Boundary 1 (Transport & Network):** The browser is an untrusted execution environment. Local storage of bearer tokens is vulnerable to XSS. All traffic must be encrypted via TLS in production. Standard browser headers (`CSP`, `HSTS`, `X-Content-Type-Options`) enforce client-side sandbox restrictions.
2. **Trust Boundary 2 (Client-to-API Perimeter):** Unauthenticated endpoints (`/api/auth/**`, `/api/verification/confirm`, `/api/disclosure/**`) are exposed to brute-force and credential stuffing. Rate limiting must throttle unauthenticated request bursts.
3. **Trust Boundary 3 (Authentication & Authorization):** Controllers must derive identity solely from authenticated `UserPrincipal`. Client-supplied `ownerId`, `userId`, or `actorId` must never be trusted. Failure to provide a principal must fail closed (`401` or `403`), never fall back to request parameters.
4. **Trust Boundary 4 (Cryptographic & Storage Boundary):** The document storage layer must prevent path traversal out of the configured root directory. Decryption must authenticate AES-256-GCM tags before returning plaintext. Authorization must precede decryption.

---

## 4. Adversarial Attack Matrix

| Ref | Category | Threat / Attack Case | Target Component | Expected Behavior | Test Verification Method |
|---|---|---|---|---|---|
| **ATK-01** | Authentication | Credential Brute Force / Stuffing | `POST /api/auth/login` | Multiple failed logins trigger rate limiting; invalid passwords return safe 401/403 without timing/user enumeration. | `AuthSecurityAbuseTest` |
| **ATK-02** | Authentication | Registration Flood / Abuse | `POST /api/auth/register` | Excessive registration requests from single client are throttled; duplicate emails fail cleanly (400) without leaking stack. | `AuthSecurityAbuseTest` |
| **ATK-03** | Authentication | Token Replay After Logout | `POST /api/auth/logout` | Token is deleted from DB; subsequent requests with revoked token return 401. | `AuthSecurityAbuseTest` |
| **ATK-04** | Authentication | Malformed / Stolen / Expired Token | `TokenAuthenticationFilter` | Empty, garbage, expired, or tampered tokens are rejected with 401; no principal is populated. | `AuthSecurityAbuseTest` |
| **ATK-05** | Authorization | Cross-User Will Access (IDOR) | `GET /api/wills/{id}` | User B attempting to view User A's will receives 403 Forbidden. | `AuthorizationIdorSecurityTest` |
| **ATK-06** | Authorization | Cross-User Estate Manipulation | `POST /api/wills/{A}/assets` | User B attempting to add/delete assets on User A's will receives 403 Forbidden. | `AuthorizationIdorSecurityTest` |
| **ATK-07** | Authorization | Cross-User Allocation Poisoning | `POST /api/wills/{A}/allocations` | User attempting to allocate assets belonging to another will receives rejection (400/403). | `AuthorizationIdorSecurityTest` |
| **ATK-08** | Authorization | Missing Principal Fail-Closed | `EstateController`, `DocumentController` | Requests without authenticated principal fail closed immediately; client-provided `ownerId`/`actorId` ignored. | `AuthorizationIdorSecurityTest` |
| **ATK-09** | State Machine | Illegal Transition Skipping | `WillStateService`, `TransitionRegistry` | Directly forcing `ACTIVE -> EXECUTED` or `ACTIVE -> VERIFIED` is rejected by transition registry; state unchanged. | `StateMachineAbuseSecurityTest` |
| **ATK-10** | State Machine | Terminal State Reversal | `WillStateService` | Attempting any transition or reset from `EXECUTED` fails; state remains `EXECUTED`. | `StateMachineAbuseSecurityTest` |
| **ATK-11** | Concurrency | Concurrent State Claim Race | `AtomicWillTransitionRepository` | Two workers attempting same conditional transition; exactly one succeeds, loser safely gets 0 rows updated. | `WillStateConcurrencyTest`, `StateMachineAbuseSecurityTest` |
| **ATK-12** | Verification | Duplicate Confirmation by Same Contact | `VerificationService` | Same contact confirming twice in same cycle is rejected via DB unique constraint; distinct count unchanged. | `VerificationAbuseSecurityTest` |
| **ATK-13** | Verification | Stale Cycle Token Replay | `VerificationService` | Tokens issued for cycle $N$ used in cycle $N+1$ after owner reset are rejected (`410 Gone` / revoked). | `VerificationAbuseSecurityTest` |
| **ATK-14** | Verification | Token Brute Force / Spraying | `POST /api/verification/confirm` | Invalid tokens fail closed; rate limiter throttles rapid guessing attempts. | `RateLimitingSecurityTest` |
| **ATK-15** | Cryptography | Ciphertext Tampering (Bit-flip) | `AesGcmEnvelopeEncryptionService` | Any modification to ciphertext or 128-bit GCM tag triggers fail-closed `DecryptionFailedException`. | `CryptoAndStorageSecurityTest` |
| **ATK-16** | Cryptography | Wrapped DEK Tampering | `AesGcmEnvelopeEncryptionService` | Tampered wrapped DEK fails RFC 3394 unwrap; `DecryptionFailedException` thrown; zero plaintext returned. | `CryptoAndStorageSecurityTest` |
| **ATK-17** | Storage | Path Traversal via Relative Paths | `LocalDocumentStorageService` | `../secret`, `..\secret`, `C:\secret`, `/etc/passwd` rejected via `SecurityException`. | `CryptoAndStorageSecurityTest` |
| **ATK-18** | Disclosure | Cross-Beneficiary Package Access | `GET /api/disclosure/{token}/document/{id}` | Beneficiary attempting to download document outside their allocated package receives 403 Forbidden. | `CryptoAndStorageSecurityTest` |
| **ATK-19** | Disclosure | Pre-Execution Premature Access | `DisclosureService` | Accessing disclosure before will reaches `EXECUTED` state returns 409 Conflict (`DISCLOSURE_NOT_READY`). | `CryptoAndStorageSecurityTest` |
| **ATK-20** | Disclosure | Disclosure Token Replay / Race | `DisclosureService.consumeDisclosure` | Single-use token atomically marked consumed; replay attempt returns 409 Conflict. | `CryptoAndStorageSecurityTest` |
| **ATK-21** | Audit | Chain Tampering (Payload Alteration) | `AuditLogService.verifyGlobalIntegrity` | Modifying payload, sequence, or timestamp of historical entry $N$ breaks hash chain at $N$ and $N+1$. | `AuditAndReleaseSecurityTest` |
| **ATK-22** | Audit | Chain Deletion / Gap Injection | `AuditLogService.verifyGlobalIntegrity` | Deleting entry $N$ causes sequence gap and previous-hash mismatch detection. | `AuditAndReleaseSecurityTest` |
| **ATK-23** | Internal Jobs | Missing / Incorrect Job Secret | `POST /internal/jobs/**` | Unauthenticated calls or bad secrets return 401 Unauthorized; secret comparison is constant-time. | `AuditAndReleaseSecurityTest` |
| **ATK-24** | Release | Duplicate Worker Release Execution | `ReleaseExecutionService` | Concurrent execution attempts result in single execution; items processed effectively once. | `AuditAndReleaseSecurityTest` |
| **ATK-25** | Configuration | Wildcard CORS with Credentials | `SecurityConfig` | Restrict allowed origins to designated origins; remove wildcard origin pattern with credentials. | `ProductionSecurityConfigurationTest` |
| **ATK-26** | Configuration | Test Routes Exposed in Production | `SecurityConfig` | Remove `/test/errors/**` permitAll matcher from production filter chain. | `ProductionSecurityConfigurationTest` |

---

## 5. Security Invariant Guarantees

1. **Server Authoritative:** State transitions are determined solely by server-side business rules and atomic database updates. Client input cannot dictate transitions.
2. **Fail-Closed Operations:** Any exception during authentication, authorization, cryptographic decryption, verification, or audit persistence halts execution and rolls back transactional changes.
3. **Partitioned Disclosure:** Disclosures are scoped per beneficiary. A beneficiary has zero visibility into allocations or documents intended for other beneficiaries.
4. **Idempotent Background Processing:** Workers can crash, restart, or run concurrently without duplicating release disclosures or corrupting succession state.
5. **Tamper-Evident Auditability:** Every critical domain operation is recorded in an unbroken cryptographic SHA-256 hash chain with fail-closed transactional guarantees.
