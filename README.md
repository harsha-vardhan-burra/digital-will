# Digital Will

A secure, deterministic digital estate succession management platform built with Spring Boot and Next.js.

Digital Will enables individuals to document digital and physical assets, specify beneficiaries, configure trusted contacts, and ensure controlled, cryptographically guarded succession execution when predetermined inactivity and verification criteria are met.

---

## 1. What Digital Will Is (and Is Not)

- **It IS:** A secure platform for documenting digital and physical assets, specifying beneficiaries, configuring trusted contacts, and conditionally releasing envelope-encrypted estate records and instructions to designated beneficiaries when predetermined inactivity and 2-of-3 trusted contact verification criteria are met.
- **It is NOT:**
  - A legally valid will or testament under any jurisdiction.
  - A legal instrument establishing heirship, probate validity, or inheritance rights.
  - An automated banking, wire transfer, or financial transaction execution system.
  - A mechanism that confers legal transfer of title, deed, or physical/financial property.
  - A password manager, credentials vault, or service that controls or shuts down third-party accounts.

---

## 2. Architecture & Tech Stack

Digital Will is architected as a modular monolith:

### Backend
- **Language / Runtime:** Java 21
- **Framework:** Spring Boot 3.3.4 (Spring Web, Spring Security, Spring Data JPA)
- **Database:** PostgreSQL (with Flyway database migrations)
- **Testing:** JUnit 5, Mockito, Spring Boot Test, H2 in-memory test database
- **Cryptography:** Java Cryptography Extension (AES-256-GCM envelope encryption, SHA-256 hashing)

### Frontend
- **Framework:** Next.js 15 (App Router, React 19)
- **Language:** TypeScript
- **Styling & Components:** Tailwind CSS, Lucide icons, clsx, tailwind-merge
- **State & Data Fetching:** Native React hooks and fetch API with strict error boundaries

---

## 3. Core Architectural Guarantees

### 3.1 Server-Authoritative State Engine
The lifecycle of every will progresses through an authoritative sequence:
```text
ACTIVE -> INACTIVITY_WARNING -> FINAL_WARNING -> VERIFICATION_PENDING -> VERIFIED -> RELEASE_PENDING -> EXECUTING -> EXECUTED
```
- **Guarded Transitions:** Transitions are performed via conditional database updates.
- **No Skipping:** Intermediate states cannot be bypassed.
- **Owner Reset Supremacy:** Any verified owner activity prior to `EXECUTED` resets the will back to `ACTIVE`, clears warnings, and invalidates pending verification tokens.
- **Crash Recovery:** A stalled release execution in `EXECUTING` is automatically recovered back to `RELEASE_PENDING` once its execution lease expires.

### 3.2 AES-256-GCM Envelope Encryption
- Sensitive documents are encrypted with AES-256-GCM before storage.
- Each document uses an ephemeral Data Encryption Key (DEK) wrapped by a Master Key (KEK).
- Every encryption generates a unique 96-bit random IV; plaintexts and DEKs are never logged or stored unencrypted.
- Decryption enforces fail-closed GCM authentication tag verification.

### 3.3 Controlled Estate Disclosure
- Disclosures are strictly partitioned: beneficiaries only see the specific assets and documents allocated to them.
- Access is granted via cryptographically random 256-bit single-use tokens hashed with SHA-256 at rest.
- Server-side scope verification guarantees that a beneficiary cannot download documents outside their allocated package.

### 3.4 Tamper-Evident Hash-Chained Audit Logging
- Every critical action (state changes, uploads, verification events, disclosures) is written to a per-will hash chain.
- Entries are linked using SHA-256 hashes including the previous hash and entry metadata.
- **Tamper-Evident, Not Immutable:** While database storage is subject to potential physical modification, any unauthorized alteration, row insertion, or row deletion breaks the cryptographic hash chain and is immediately flagged upon verification.
- Concurrency serialization guarantees monotonic sequence numbers across concurrent worker commits.

### 3.5 Protected Background Job Processing & Orchestration
- Scheduled processing endpoints (`/internal/jobs/**`) trigger state machine evaluations, recovery, and release disbursements.
- Endpoints are protected by a shared secret (`X-Internal-Job-Secret`) verified using constant-time comparison to prevent timing attacks.
- External orchestration is automated via GitHub Actions (`.github/workflows/scheduled-jobs.yml`), which executes on a 15-minute schedule or on-demand dispatch with strict secret and environment validation.

### 3.6 Transaction-Aware Notification Delivery
- Notification delivery (`NotificationDeliveryService`) supports automated email dispatch for trusted-contact verification and beneficiary disclosure.
- **Post-Commit Delivery:** Notifications are dispatched via `TransactionSynchronizationManager.afterCommit()` so emails are sent only when database state transitions successfully commit.
- **Provider Decoupling:** Defaults to `DISABLED` for local development and testing (returning `SKIPPED_UNCONFIGURED` without failing the workflow). Supports `CONSOLE` for local debugging and `RESEND` for production email dispatch.
- **PII & Token Privacy:** Logs mask email addresses (`u***@example.com`) and never print raw verification or disclosure tokens.

---

## 4. Phase 3 Core Capabilities & Workflows

### 4.1 Authentication & Server-Side Ownership Boundary
- **User Registration & Login:** Email/password authentication using BCrypt (`strength = 12`) password hashing.
- **Bearer Token Management:** Session tokens hashed via SHA-256 in PostgreSQL (`user_auth_tokens`).
- **Principal Derivation:** All will and estate operations derive ownership strictly from the authenticated `UserPrincipal`. Client-supplied user IDs are never trusted.
- **Single Will per User Model:** Enforced at both the database schema (`uq_wills_owner_user_id`) and service layer, accessible via `GET /api/wills/my`.
- **Authorization Before Decryption:** Document access requires authenticated ownership verification before any decryption operation is initiated.

### 4.2 Comprehensive Estate & Allocation Management
- **Structured Asset Categories:** Real Estate, Bank Accounts, Investment Portfolios, and Digital Accounts.
- **Independent Beneficiary Registry:** Beneficiaries are stored independently of assets to permit fine-grained allocation.
- **Strict Allocation Enforcement:** Allocations are verified to belong to the owner's will, reference valid assets, and enforce a 100% maximum distribution rule per asset.
- **Review & Readiness Checklist:** `GET /api/wills/{id}/review` evaluates readiness against 6 key operational criteria before succession enablement.

### 4.3 Five Core Integration Workflows
The system is backed by a 209-test backend suite (209 of 209 tests passing, with 66 dedicated Phase 4 security tests and 10 notification delivery tests):
1. **Workflow 1: End-to-End Estate Setup & Review (`EstateWorkflowIntegrationTest`)**
   - User registration -> will creation -> asset and beneficiary configuration -> 100% allocation -> 3 trusted contacts -> document vault upload -> review checklist verification -> activity check-in -> audit log integrity check.
2. **Workflow 2: Ownership Boundaries & Single Will Isolation (`WillOwnershipIntegrationTest`)**
   - Verifies User B receives `403 Forbidden` attempting to access User A's will, assets, beneficiaries, contacts, documents, or check-ins. Verifies single-will conflict returns `409 Conflict`.
3. **Workflow 3: Succession Progression & Inactivity Lifecycle (`SuccessionIntegrationTest`)**
   - Authoritative progression through `ACTIVE` -> `INACTIVITY_WARNING` -> `FINAL_WARNING` -> `VERIFICATION_PENDING` -> 2-of-3 Contact Quorum -> `VERIFIED` -> `RELEASE_PENDING` -> `EXECUTING` -> `EXECUTED`, with complete audit hash-chain continuity.
4. **Workflow 4: Release Failure Recovery & Idempotent Resumption (`ReleaseFailureRecoveryIntegrationTest`)**
   - Simulates a mid-release worker crash after 1 of 2 beneficiaries is processed. Verifies expired lease recovery, idempotency, skipping already executed items, and zero duplicate disclosure entries.
5. **Workflow 5: Scoped Disclosure Isolation & Fail-Closed Boundaries (`DisclosureWorkflowIntegrationTest`)**
   - Verifies strict beneficiary payload isolation (Beneficiary A cannot see Beneficiary B's assets), envelope-decrypted document downloads, cross-beneficiary document download rejection (`403 Forbidden`), expired token rejection (`410 Gone`), and single-use token consumption (`409 Conflict`).

---

## 5. Phase 4 Security Verification & Production Hardening

Phase 4 subjected the platform to adversarial security verification against 26 attack vectors documented in [`docs/threat-model-phase4.md`](docs/threat-model-phase4.md):
- **Adversarial Test Suites (66 Security Tests):**
  - `AuthSecurityAbuseTest` (7 tests): Credential stuffing defense, generic error masking, duplicate registration, logout token invalidation, 30-day token expiry, malformed/oversized headers.
  - `AuthorizationIdorSecurityTest` (15 tests): Cross-user IDOR prevention across wills, assets, beneficiaries, allocations, contacts, documents, and audit logs; null-principal fail-closed controller enforcement; cross-will allocation poisoning prevention.
  - `StateMachineAbuseSecurityTest` (7 tests): Invalid state skipping prevention, immutable terminal `EXECUTED` state, multi-threaded transition race isolation (8 threads, 1 winner), owner activity reset.
  - `VerificationAbuseSecurityTest` (7 tests): Duplicate contact confirmation rejection, token idempotency, stale cycle token replay (410 Gone), expired/forged tokens, 2-of-3 quorum validation.
  - `CryptoAndStorageSecurityTest` (9 tests): AES-256-GCM CPA security (unique IVs), AEAD ciphertext and AAD tamper detection, DEK corruption detection, path traversal rejection (`..`, `/etc/passwd`, absolute paths), single-use disclosure token replay defense.
  - `AuditAndReleaseSecurityTest` (12 tests): Audit hash chain tamper detection (payload, prev_hash, entry_hash, row deletion), fail-closed `logCritical` transaction rollback, internal job secret verification (`X-Internal-Job-Secret`), release execution idempotency, crash recovery lease expiration.
  - `RateLimitingSecurityTest` (5 tests): Sliding-window burst mitigation on login, registration, verification confirmation, and disclosure access returning `429 Too Many Requests` with `Retry-After`.
  - `ProductionSecurityConfigurationTest` (4 tests): Production security headers (CSP, HSTS, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy), strict CORS origin whitelisting, Actuator restriction, stack trace masking.
- **Fail-Closed Controller Hardening:** Controllers enforce server-derived identity exclusively; client-supplied owner IDs are ignored and null principals trigger immediate security exceptions.
- **Concurrency Serialization:** Audit log entries are synchronized across database commit boundaries to guarantee strictly monotonic sequences under multi-worker concurrency.

---

## 6. API Endpoints

| Category | Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- | :--- |
| **Auth** | `POST` | `/api/auth/register` | Register user account | Public |
| **Auth** | `POST` | `/api/auth/login` | Authenticate and obtain Bearer token | Public |
| **Auth** | `GET` | `/api/auth/me` | Retrieve authenticated user profile | Bearer Token |
| **Auth** | `POST` | `/api/auth/logout` | Revoke current Bearer token | Bearer Token |
| **Wills** | `POST` | `/api/wills` | Create a new Digital Will (1 per user) | Bearer Token |
| **Wills** | `GET` | `/api/wills/my` | Get current user's Digital Will | Bearer Token |
| **Wills** | `GET` | `/api/wills/{id}` | Get will details (owner only) | Bearer Token |
| **Wills** | `GET` | `/api/wills/{id}/review` | Comprehensive readiness checklist | Bearer Token |
| **Wills** | `GET` | `/api/wills/{id}/audit` | Sanitized audit log trail | Bearer Token |
| **Wills** | `POST` | `/api/wills/{id}/check-in` | Record owner activity check-in | Bearer Token |
| **Estate** | `POST` | `/api/wills/{id}/assets` | Add asset to will | Bearer Token |
| **Estate** | `DELETE` | `/api/wills/{id}/assets/{assetId}` | Remove asset and allocations | Bearer Token |
| **Estate** | `POST` | `/api/wills/{id}/beneficiaries` | Add beneficiary to will | Bearer Token |
| **Estate** | `DELETE` | `/api/wills/{id}/beneficiaries/{benId}`| Remove beneficiary & allocations | Bearer Token |
| **Estate** | `POST` | `/api/wills/{id}/allocations` | Define asset distribution share | Bearer Token |
| **Estate** | `DELETE` | `/api/wills/{id}/allocations/{allocId}`| Remove asset allocation | Bearer Token |
| **Contacts** | `GET` | `/api/wills/{id}/contacts` | List trusted contacts & status | Bearer Token |
| **Contacts** | `POST` | `/api/wills/{id}/contacts` | Add trusted contact | Bearer Token |
| **Contacts** | `DELETE` | `/api/wills/{id}/contacts/{contactId}` | Deactivate trusted contact | Bearer Token |
| **Documents** | `POST` | `/api/documents/upload` | Upload & envelope encrypt document | Bearer Token |
| **Documents** | `GET` | `/api/documents/{id}/download` | Stream decrypt & download document | Bearer Token |
| **Documents** | `GET` | `/api/documents/will/{willId}` | List encrypted documents for will | Bearer Token |
| **Verification** | `POST` | `/api/verification/confirm` | Confirm 2-of-3 contact verification | Contact Token (`token` body) |
| **Disclosure** | `GET` | `/api/disclosure/{token}` | Scoped estate disclosure package | Single-Use Token |
| **Disclosure** | `GET` | `/api/disclosure/{token}/document/{docId}` | Scoped decrypted document download | Single-Use Token |
| **Jobs** | `POST` | `/internal/jobs/process-inactivity` | Process inactivity warnings & verification | `X-Internal-Job-Secret` |
| **Jobs** | `POST` | `/internal/jobs/recover-stalled-executions` | Recover expired release worker leases | `X-Internal-Job-Secret` |
| **Jobs** | `POST` | `/internal/jobs/process-releases` | Process and disburse release packages | `X-Internal-Job-Secret` |

---

## 7. Getting Started

### Prerequisites
- Java 21 JDK
- Node.js 18+ and npm
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- PostgreSQL 15+ (for live application startup; automated tests use in-memory H2)

### Environment Configuration
Copy `.env.example` to configure the backend and frontend environments:
```bash
cp .env.example .env
```
Key configuration items:
- `MASTER_ENCRYPTION_KEY`: Base64-encoded 256-bit key for document envelope encryption.
- `INTERNAL_JOB_SECRET`: Secret header required for internal scheduled jobs (`X-Internal-Job-Secret`).
- `NOTIFICATION_ENABLED`: Enable email dispatch (`true`/`false`, default `false`).
- `NOTIFICATION_PROVIDER`: Notification provider (`DISABLED`, `CONSOLE`, or `RESEND`).
- `RESEND_API_KEY`: API key if using Resend email provider.

### Running Backend Tests
All 209 tests execute in-memory using H2 with Flyway PostgreSQL compatibility:
```bash
cd backend
./mvnw clean test
```

### Running the Backend Server
> **Note on Local Database Provisioning:** Live runtime execution requires a running PostgreSQL instance with configured credentials. PostgreSQL provisioning and credential management are handled separately by the Estate & Backend Engineer.
```bash
cd backend
./mvnw spring-boot:run
```

### Running the Frontend
```bash
cd frontend
npm install
npm run dev
```

---

## 8. Security & Documentation

For detailed security guidelines and architectural models, consult:
- [SECURITY.md](SECURITY.md): Cryptographic model, storage security, and authentication boundary.
- [docs/threat-model-phase4.md](docs/threat-model-phase4.md): Phase 4 Threat Model and 26-vector attack matrix.
- [docs/architecture-phase2.md](docs/architecture-phase2.md): Comprehensive Phase 2 architectural specification.
- [docs/state-machine.md](docs/state-machine.md): Authoritative state engine invariants and transitions.
