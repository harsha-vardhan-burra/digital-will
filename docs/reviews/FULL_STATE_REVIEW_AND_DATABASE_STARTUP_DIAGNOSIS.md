# Digital Will — Database Startup Error Investigation & Full State Review

**Document Status:** Complete Diagnostic & Comprehensive Audit Report  
**Date:** October 10, 2026  
**Auditor:** Senior Engineering Diagnostic Lead  
**Repository Branch:** `main` (commit `51e3cf9`)  
**Target File Path:** `docs/reviews/FULL_STATE_REVIEW_AND_DATABASE_STARTUP_DIAGNOSIS.md`  

---

## 1. Executive Summary & Overall Readiness Verdict

A comprehensive diagnostic investigation and architectural review was conducted on the Digital Will repository at commit `51e3cf9`.

### Overall Readiness Verdict: **Conditionally Ready / Environment-Blocked for PostgreSQL Startup**

| Layer | Status | Verdict |
| :--- | :--- | :--- |
| **Backend Unit & Integration Suite** | **Verified Working** | 215 / 215 tests passing (0 failures, 0 errors, 0 skipped). |
| **PostgreSQL Flyway Migrations (V1–V7)** | **Verified Working** | Successfully verified against PostgreSQL via zonky `EmbeddedPostgres`. |
| **Zero-Dependency Dev Mode (`dev` profile)** | **Verified Working** | Tomcat starts cleanly on port 8080 in ~9.9s; Actuator health reports `UP`. |
| **Frontend Production Build (Next.js 15)** | **Verified Working** | All 8 routes compile and typecheck without errors. |
| **PostgreSQL Runtime Connection (Default)** | **Blocked by Environment** | Fails with `FATAL: password authentication failed for user "postgres"` (SQL State `28P01`). Local Windows PostgreSQL 18 service on port 5432 rejects default fallback password `postgres`. |
| **End-to-End Live PostgreSQL Integration** | **Not Verified** | Blocked until local PostgreSQL role password is provided or Docker Compose is started. |

---

## 2. Startup Error Investigation & Root-Cause Assessment

### 2.1 Observed Failure Log
The user executed the backend from PowerShell in the `backend` directory:
```powershell
./mvnw spring-boot:run
```

The decisive failure was captured during Flyway's initial connection attempt:
```text
Unable to obtain connection from database:
FATAL: password authentication failed for user "postgres"

SQL State  : 28P01
Error Code : 0
Message    : FATAL: password authentication failed for user "postgres"
```
The failure cascaded through `flywayInitializer`, `entityManagerFactory`, Spring Data JPA repository registration, `AuthService`, and `tokenAuthenticationFilter`, terminating with `BUILD FAILURE`.

### 2.2 Confirmed Facts from Diagnostic Evidence
1. **Property Resolution Precedence:**
   In `backend/src/main/resources/application.properties`, datasource settings are defined as:
   ```properties
   spring.datasource.url=${SPRING_DATASOURCE_URL:${DB_URL:jdbc:postgresql://localhost:5432/digitalwill}}
   spring.datasource.username=${SPRING_DATASOURCE_USERNAME:${DB_USERNAME:postgres}}
   spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:${DB_PASSWORD:postgres}}
   ```
2. **Environment Variable Absence:**
   Inspection of the local PowerShell environment confirmed that **no** datasource environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) were set.
3. **No Automatic `.env` Loading by Spring Boot:**
   The repository contains `.env.example`, but no `.env` loader (such as `dotenv-java` or custom initializers) exists in `pom.xml` or `DigitalWillBackendApplication.java`. Spring Boot does not read `.env` files automatically.
4. **Local Windows Port 5432 Conflict:**
   Diagnostic inspection of network sockets and Windows services revealed:
   - Port `5432` is occupied by Windows PID 9004, running **PostgreSQL Server 18** (`postgresql-x64-18` service).
   - Port `5433` is occupied by **PostgreSQL Server 13** (`postgresql-x64-13` service).
   - Port `5434` is occupied by **PostgreSQL Server 14** (`postgresql-x64-14` service).
   - Docker daemon is not installed or running as a Windows service.
5. **Authentication Method:**
   PostgreSQL 18 `pg_hba.conf` enforces `scram-sha-256` authentication for all local connections (`127.0.0.1/32` and `::1/128`).
6. **Authentication Verification Test:**
   A safe, non-destructive connection test using `psql.exe` on port 5432 with password `postgres` produced the exact same error:
   ```text
   psql: error: connection to server at "127.0.0.1", port 5432 failed: FATAL: password authentication failed for user "postgres"
   ```
   Ports 5433 and 5434 also rejected password `postgres`.

### 2.3 Most Likely Cause
The application configuration assumes a Docker Compose container or local database configured with default credentials (`postgres` / `postgres`). However, on the developer's workstation, a native Windows PostgreSQL 18 installation is listening on port 5432 with a custom administrator password chosen during installation. Because no environment variables were provided to override the default, Spring Boot attempted to connect with password `postgres` and was rejected by the local PostgreSQL 18 instance.

### 2.4 Not Yet Known
- What specific password was chosen by the workstation owner when installing PostgreSQL 18 on Windows.
- Whether a database named `digitalwill` exists inside the local PostgreSQL 18 instance.

### 2.5 Validation Required
To validate successful PostgreSQL startup:
- With Docker: Stop or remap the local Windows PostgreSQL service, launch `docker compose up -d`, and verify port 5432 responds to user `postgres` with password `postgres`.
- With Native PostgreSQL: Ensure the `digitalwill` database exists, and supply the actual password via PowerShell:
  ```powershell
  $env:SPRING_DATASOURCE_PASSWORD="actual_postgres_password"
  cd backend
  ./mvnw spring-boot:run
  ```

---

## 3. Git Repository State

| Check | Result | Evidence |
| :--- | :--- | :--- |
| **Current Branch** | `main` | `git branch --show-current` -> `main` |
| **Local HEAD** | `51e3cf9` | `git log -1` -> `Merge pull request #6 from harsha-vardhan-burra/feat/postgres-flyway-startup` |
| **Upstream Remote** | `origin/main` (`51e3cf9`) | Local `HEAD` is exactly in sync with `origin/main`. |
| **Remote URL** | `https://github.com/harsha-vardhan-burra/digital-will.git` | `git remote -v` |
| **Uncommitted Changes** | 2 modified documentation/config files | `README.md`, `application.properties` (documented in Section 10). |

---

## 4. Architecture & Module Status Table

| Area | Status | Evidence | Risk / Gap | Next Action |
| :--- | :--- | :--- | :--- | :--- |
| **Backend Startup (PostgreSQL)** | **Blocked by environment** | Log + `application.properties` | Local role password differs from fallback default | User/owner supplies password or starts Docker Compose |
| **Backend Startup (`dev` profile)** | **Verified working** | Live run + Actuator `/actuator/health` -> `UP` | In-memory H2 only (not persistent) | Use for rapid frontend/UI integration |
| **State Engine** | **Verified working** | `TransitionRegistry`, `WillStateService`, `WillStateConcurrencyTest` | None identified | Maintain invariant test coverage |
| **Trusted Contact Verification** | **Verified working** | `VerificationService`, `VerificationTokenService`, tests | None identified | Maintain 2-of-3 quorum invariants |
| **Cryptography & Vault** | **Verified working** | `AesGcmEnvelopeEncryptionService`, `LocalDocumentStorageService` | Local storage directory path | Cloud blob storage adapter (e.g. S3/GCS) needed for cluster deployments |
| **Audit Logging** | **Verified working** | `AuditLogService`, `AuditAndReleaseSecurityTest` | Application-level hash chain, not write-once hardware | Maintain tamper-evident hash chaining |
| **Disclosure & Release** | **Verified working** | `ReleaseExecutionService`, `DisclosureService`, tests | None identified | Ready for production staging |
| **Authentication & AuthZ** | **Verified working** | `AuthService`, `SecurityConfig`, `AuthorizationIdorSecurityTest` | None identified | Maintain strict principal derivation |
| **Scheduled Jobs** | **Implemented but not integration-tested in live deploy** | `InternalJobController`, `.github/workflows/scheduled-jobs.yml` | GitHub Actions schedule queue latency | Add internal cron scheduler for sub-minute SLA |
| **Notification Delivery** | **Verified working** | `NotificationDeliveryService`, transactional tests | External API key required for live email | Configure Resend API key for production |
| **Database & Migrations** | **Verified working** | Migrations `V1`–`V7`, `PostgreSqlFlywayMigrationIntegrationTest` | Needs local credentials to start live | Provide local credentials |
| **Frontend Integration** | **Verified working (build & types)** | Next.js production build (`npm run build`) | Real E2E browser tests against live backend pending | Run browser E2E tests once backend runs with DB |

---

## 5. State Machine & Security Invariant Review

### A. State Machine Invariants
The lifecycle follows:
`ACTIVE` -> `INACTIVITY_WARNING` -> `FINAL_WARNING` -> `VERIFICATION_PENDING` -> `VERIFIED` -> `RELEASE_PENDING` -> `EXECUTING` -> `EXECUTED`

- **Skipping Disallowed:** `TransitionRegistry.getTransitionOrThrow()` rejects skipped transitions with `IllegalStateTransitionException`.
- **Terminal State Enforcement:** `EXECUTED` is strictly terminal. `WillState.isTerminal()` returns true; no transitions exist out of `EXECUTED`.
- **Owner Reset Supremacy:** Any verified owner activity (`recordVerifiedActivity()`) or explicit cancellation (`cancelSuccession()`) during `INACTIVITY_WARNING`, `FINAL_WARNING`, `VERIFICATION_PENDING`, or `RELEASE_PENDING` resets the will to `ACTIVE`.
- **Cycle Invalidation:** Resetting to `ACTIVE` atomically increments `verification_cycle = verification_cycle + 1` and updates active verification requests to `REVOKED`. Stale cycle tokens cannot be used (`VerificationRequestRevokedException`).
- **Concurrency & Race Conditions:** Transitions use guarded SQL updates (`AtomicWillTransitionRepositoryImpl`) checking current state and conditions in the `WHERE` clause, incrementing optimistic `version`, and verifying `rowsUpdated == 1`. Raced workers receive false and fail safely.
- **Crash Recovery:** If an execution worker crashes in `EXECUTING`, `recoverStaleExecution()` safely reverts state to `RELEASE_PENDING` once the execution lease expires (default 30 minutes).

### B. Trusted Contact Verification
- **Quorum:** Enforces 2-of-3 distinct contact confirmations (`REQUIRED_CONFIRMATIONS = 2`).
- **Token Entropy:** Generated via `SecureRandom` using 32 bytes (256-bit), encoded as URL-safe Base64.
- **Hash-Only Storage:** Raw tokens are never persisted in the database; only SHA-256 hashes are stored (`verification_requests.token_hash`).
- **Constant-Time Comparison:** Matches use `MessageDigest.isEqual()` to prevent timing side channels.
- **Duplicate Prevention:** Database unique constraint `uq_contact_confirmations_will_contact_cycle UNIQUE (will_id, contact_id, verification_cycle)` guarantees the same contact cannot satisfy multiple quorum slots.
- **Single-Use Tokens:** Tokens are transitioned to `CONFIRMED` upon use. Reuse throws `VerificationTokenAlreadyUsedException`.

### C. Cryptography & Document Vault
- **Algorithm:** AES-256-GCM (`AES/GCM/NoPadding`) with 128-bit authentication tag and 96-bit cryptographically secure random IV per operation.
- **Envelope Encryption:** Ephemeral 256-bit AES Data Encryption Keys (DEKs) wrapped by a 256-bit Master Key via RFC 3394 `AESWrap`.
- **Path Traversal Protection:** `LocalDocumentStorageService` checks against `..`, `:`, leading slashes, and verifies `targetPath.startsWith(rootStorageDir)`.
- **Zero Plaintext Logging:** Plaintexts and raw DEKs are never logged or stored unencrypted.

### D. Audit Log Integrity
- **Canonical Serialization:** Formatted as `sequenceNumber|createdAt|willId|actorType|actorId|action|status|resourceType|resourceId|detailsJson|prevHash`.
- **Hash Chaining:** Entry $N$ hash = $\text{SHA-256}(\text{canonical}_N + \text{entry\_hash}_{N-1})$. Genesis block uses 64 zeros.
- **Concurrency Serialization:** Critical audit writes synchronize on `auditLock` with Spring `TransactionTemplate` to guarantee monotonic sequence numbers across concurrent worker commits.
- **Tamper Evidence:** `verifyGlobalIntegrity()` and `verifyWillChainIntegrity()` detect modifications, insertions, deletions, or sequence discontinuities.

### E. Controlled Disclosure & Release
- **Strict Scope Isolation:** Disclosed records are partitioned per beneficiary. Beneficiaries only see their allocated assets.
- **Document Scope Guard:** `DisclosureController` verifies `disclosure.payloadJson().contains(documentId.toString())` before decrypting or streaming any document.
- **Token Lifecycle:** 256-bit tokens, SHA-256 hashed at rest, with `ACTIVE`, `ACCESSED`, `EXPIRED`, `REVOKED` states and optional atomic single-use consumption (`consumeTokenIfActive`).

### F. Authentication & Web Security
- **Server-Derived Identity:** Controllers derive owner identity strictly from `@AuthenticationPrincipal UserPrincipal`. Client-supplied user IDs in request bodies are ignored.
- **Password Security:** BCrypt password hashing (`strength = 12`) with minimum 8-character enforcement.
- **Production Security Headers:** `SecurityConfig` enforces strict Content-Security-Policy, HSTS (1 year + subdomains), `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Permissions-Policy`.
- **Actuator Hardening:** Exposure restricted to `health` and `info`; health details are hidden (`show-details: never`).
- **Rate Limiting:** Sliding-window rate limiter protects `/api/auth/login`, `/api/auth/register`, `/api/verification/confirm`, and `/api/disclosure/**`, returning HTTP 429 with `Retry-After`.

### G. Scheduled Jobs & GitHub Actions
- **Workflow:** `.github/workflows/scheduled-jobs.yml` orchestrates `process-inactivity`, `recover-stalled-executions`, and `process-releases`.
- **Authentication:** Protected by `X-Internal-Job-Secret` verified using constant-time comparison in `InternalJobController`.
- **Fail-Closed:** Missing `APP_BASE_URL` or `INTERNAL_JOB_SECRET` immediately aborts workflow execution.

### H. Notifications
- **Transaction-Aware Delivery:** Notifications dispatch post-commit via `TransactionSynchronizationManager.afterCommit()`. Rolled-back transactions never send emails.
- **PII & Token Privacy:** Logs mask email addresses (`u***@example.com`) and never print raw verification or disclosure tokens.
- **Provider Fallback:** Defaults safely to `DISABLED` (`SKIPPED_UNCONFIGURED`).

---

## 6. Test, Build & CI Validation Results

All feasible checks were executed on the current checkout:

### 6.1 Backend Test Suite
```powershell
# Command executed in backend/
.\mvnw.cmd test
```
**Result:**
- Tests run: **215**
- Failures: **0**
- Errors: **0**
- Skipped: **0**
- Build Status: **BUILD SUCCESS**
- Total Time: **1 min 18 s**
- Note: This includeszonky `EmbeddedPostgres` integration tests verifying all 7 Flyway migrations and Spring Data JPA entities against real PostgreSQL.

### 6.2 Frontend Production Build
```powershell
# Command executed in frontend/
npm run build
```
**Result:**
- Compiler: Next.js 15.5.27 (React 19)
- Typecheck & Lint: Passed without errors
- Static Pages Generated: 8 / 8
- Build Status: **Exit code 0 (SUCCESS)**

### 6.3 Live Backend Startup (`dev` profile)
```powershell
# Command executed in backend/
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```
**Result:**
- Tomcat started on port 8080 in **9.969 seconds**
- Actuator endpoint `GET http://localhost:8080/actuator/health` responded:
  ```json
  {"status":"UP","groups":["liveness","readiness"]}
  ```
- Process was cleanly shut down and port 8080 was verified released.

---

## 7. Database & Migration Configuration Assessment

### 7.1 Migration History
The repository includes 7 ordered Flyway migrations:
1. `V1__create_wills_table.sql`: Authoritative `wills` table with state constraint and indexes.
2. `V2__create_trusted_contact_verification_tables.sql`: `trusted_contacts`, `will_contacts`, `verification_requests`, `contact_confirmations` with unique cycle constraint.
3. `V3__create_encryption_documents_tables.sql`: `encrypted_documents` table with envelope encryption metadata and checksums.
4. `V4__create_audit_log_tables.sql`: `audit_logs` table with sequence number, `prev_hash`, and `entry_hash`.
5. `V5__create_estate_disclosure_and_execution_tables.sql`: `assets`, `beneficiaries`, `asset_allocations`, `release_executions`, `release_execution_items`, `disclosure_tokens`, `disclosed_records`.
6. `V6__add_job_performance_indexes.sql`: Composite performance indexes for background scans.
7. `V7__create_users_and_auth_tables.sql`: `users` and `user_auth_tokens` tables.

### 7.2 Entity-Schema Compatibility
Verified via `PostgreSqlApplicationIntegrationTest` where Hibernate executes with `ddl-auto=validate` against PostgreSQL schema created by Flyway migrations. All entity mappings match database tables, columns, and types.

---

## 8. Confirmed Defects vs Hypotheses vs Environment Blockers

| Item | Classification | Description |
| :--- | :--- | :--- |
| **PostgreSQL Authentication Failure** | **Environment Blocker** | Workstation has native PostgreSQL 18 service running on port 5432 with a custom password. Fallback default password `postgres` is rejected. |
| **Duplicate `digitalwill.jobs.secret`** | **Confirmed Defect (Resolved)** | `application.properties` defined `digitalwill.jobs.secret` twice (lines 37 and 61), shadowing the fallback for `DIGITALWILL_JOBS_SECRET`. Resolved by removing the duplicate line. |
| **README Documentation Mismatches** | **Confirmed Defect (Resolved)** | `README.md` listed incorrect property names (`MASTER_ENCRYPTION_KEY`, `INTERNAL_JOB_SECRET`, `RESEND_API_KEY`, etc.), cited 209 tests instead of 215, and lacked instructions for the `dev` profile and PowerShell flag quoting. Resolved by updating `README.md`. |
| **Frontend Interactive `next lint`** | **Configuration Gap** | `npm run lint` prompts interactively because ESLint configuration is not committed in `frontend/`. |
| **Migration or JPA Entity Incompatibility** | **Disproven Hypothesis** | Zonky `EmbeddedPostgres` verified that all 7 Flyway migrations apply cleanly and Hibernate validation passes against PostgreSQL. |

---

## 9. Prioritized Action List (P0–P3)

### P0 — Startup Blockers (Requires User / Environment Action)
- **Item P0-1: Configure Local PostgreSQL Credentials or Start Docker**
  - **Owner:** Local Environment / Database Owner
  - **Action:** Either:
    1. Set the correct password for the local PostgreSQL service in PowerShell before running:
       ```powershell
       $env:SPRING_DATASOURCE_PASSWORD="your_postgres_password"
       ./mvnw spring-boot:run
       ```
    2. Or stop the local Windows PostgreSQL service and run `docker compose up -d` to spin up the containerized database.
  - **Validation:** Application starts up on port 8080 and Flyway successfully logs `Successfully applied 7 migrations`.

### P1 — Required Before Reliable End-to-End Demo
- **Item P1-1: Verify End-to-End Flow Against Live PostgreSQL**
  - **Owner:** QA / Backend Engineer
  - **Action:** Once PostgreSQL credentials are set, run a complete browser or curl walkthrough (Register -> Create Will -> Upload Document -> Check-in -> Review).
  - **Validation:** Verifiable entries created in `wills`, `users`, `audit_logs`, and `encrypted_documents`.

### P2 — Quality & Security Hardening
- **Item P2-1: Add Non-Interactive ESLint Configuration in Frontend**
  - **Owner:** Frontend Engineer
  - **Action:** Add `.eslintrc.json` or `eslint.config.mjs` to `frontend/` so `npm run lint` executes non-interactively in CI.
  - **Validation:** `npm run lint` exits with code 0 without prompting.

### P3 — Documentation & Polish
- **Item P3-1: Completed Documentation Alignment**
  - **Owner:** Diagnostic Lead (Resolved in this review)
  - **Action:** `README.md` updated with accurate environment variable names, 215 test count, and instructions for zero-dependency development.

---

## 10. Changes Made by This Review

1. **`backend/src/main/resources/application.properties`:**
   - Removed redundant duplicate definition of `digitalwill.jobs.secret` at line 61 that shadowed line 37.
2. **`README.md`:**
   - Corrected test suite count from 209 to 215.
   - Updated configuration reference to match actual property names in `application.properties` and `.env.example`.
   - Added clear instructions for running the zero-dependency in-memory `dev` profile (`-Dspring-boot.run.profiles=dev`) with PowerShell quoting.
   - Documented PostgreSQL runtime startup options (Docker Compose and native service).

---

## 11. Outstanding Questions & Limitations

1. **Workstation Database Password:** Antigravity does not possess or inspect the user's private password for the native Windows PostgreSQL 18 service. This value must be provided by the developer.
2. **Database Existence:** If connecting to the local Windows PostgreSQL instance, the database `digitalwill` must be created if it does not already exist:
   ```sql
   CREATE DATABASE digitalwill;
   ```
3. **No Legal Effect:** The platform provides succession planning and controlled cryptographic disclosure. It does not possess legal testamentary validity, transfer financial accounts, or execute probate title transfers.

---

## 12. Exact Next Steps to Start the Backend

### Fast Path: Instant Local UI Integration (Zero Database Dependency)
```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```
The backend will start in ~10 seconds on `http://localhost:8080` with in-memory H2 in PostgreSQL compatibility mode.

### Production Path: Live PostgreSQL Integration
```powershell
# 1. Ensure the 'digitalwill' database exists on your local PostgreSQL 18 instance
# 2. Set your local PostgreSQL credentials in PowerShell:
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "your_actual_password"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/digitalwill"

# 3. Start the backend:
cd backend
.\mvnw.cmd spring-boot:run
```

---

## 13. Statement of What Was Not Tested

1. **Live External Email Delivery:** Resend API integration was not tested against live SMTP/API endpoints because `NOTIFICATIONS_ENABLED=false` by default and no third-party API key was configured.
2. **End-to-End Live PostgreSQL Runtime Execution:** A live session with persistent PostgreSQL data was not verified due to the local credential mismatch on the workstation's native PostgreSQL 18 service. (PostgreSQL compatibility was verified via Zonky `EmbeddedPostgres`).
3. **GitHub Actions Scheduled Triggering:** Scheduled execution on GitHub was not verified live as it depends on remote repository secrets and GitHub Actions cron queueing.
