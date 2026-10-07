# Digital Will

A secure, deterministic digital estate succession management platform built with Spring Boot and Next.js.

Digital Will enables individuals to document digital and physical assets, specify beneficiaries, configure trusted contacts, and ensure controlled, cryptographically guarded succession execution when predetermined inactivity and verification criteria are met.

---

## 1. What Digital Will Is (and Is Not)

- **It IS:** A secure platform for managing, encrypting, and conditionally disclosing digital estate succession instructions, asset records, and supporting documents to designated beneficiaries.
- **It is NOT:**
  - A legal replacement for a jurisdictionally valid last will and testament.
  - An automated banking or financial transaction transfer system.
  - A password manager or credentials vault.
  - A mechanism that confers legal transfer of title or real estate ownership.

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
- **Styling & Components:** Tailwind CSS, Radix UI primitives, Lucide icons
- **Form Handling & Validation:** React Hook Form, Zod

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
- Built-in verification detects insertions, deletions, and modifications.

### 3.5 Protected Background Job Processing
- Scheduled processing endpoints (`/internal/jobs/**`) are triggered externally (e.g. GitHub Actions).
- Endpoints are protected by a shared secret (`X-Internal-Job-Secret`) verified using constant-time comparison to prevent timing attacks.


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

### 4.3 Five Core Integration Workflows — All Critical Workflows Covered
The system is backed by a 133-test backend suite (133 of 133 tests passing), including all 5 core end-to-end integration workflows:
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

## 5. API Endpoints

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
| **Verification** | `POST` | `/api/verification/verify` | Submit 2-of-3 contact verification | Contact Token |
| **Disclosure** | `GET` | `/api/disclosure/{token}` | Scoped estate disclosure package | Single-Use Token |
| **Disclosure** | `GET` | `/api/disclosure/{token}/documents/{docId}` | Scoped decrypted document download | Single-Use Token |
| **Jobs** | `POST` | `/internal/jobs/process-inactivity` | Process inactivity warnings & verification | Secret Header |
| **Jobs** | `POST` | `/internal/jobs/recover-stalled-executions` | Recover expired release worker leases | Secret Header |
| **Jobs** | `POST` | `/internal/jobs/process-releases` | Process and disburse release packages | Secret Header |

---

## 6. Getting Started

### Prerequisites
- Java 21 JDK
- Node.js 18+ and npm
- Maven 3.9+ (or use the included `./mvnw` wrapper)

### Environment Configuration
Copy `.env.example` to configure the backend and frontend environments:
```bash
cp .env.example .env
```

### Running Backend Tests
```bash
cd backend
./mvnw clean test
```
*Note: Tests execute in-memory with H2 and standard mock providers.*

### Running the Backend Server
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

## 7. Security & Documentation

For detailed security guidelines and architectural models, consult:
- [SECURITY.md](SECURITY.md): Cryptographic model, storage security, and authentication boundary.
- [docs/architecture-phase2.md](docs/architecture-phase2.md): Comprehensive Phase 2 architectural specification.
- [docs/state-machine.md](docs/state-machine.md): Authoritative state engine invariants and transitions.
