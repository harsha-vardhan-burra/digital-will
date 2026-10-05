package com.digitalwill.release.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditLogEntry;
import com.digitalwill.audit.repository.AuditLogRepository;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.exception.DisclosureNotReadyException;
import com.digitalwill.release.exception.DisclosureTokenExpiredException;
import com.digitalwill.release.exception.DisclosureTokenInvalidException;
import com.digitalwill.release.model.DisclosureToken;
import com.digitalwill.release.model.DisclosureTokenStatus;
import com.digitalwill.release.model.ExecutionStatus;
import com.digitalwill.release.repository.DisclosureTokenRepository;
import com.digitalwill.release.repository.ReleaseExecutionRepository;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.service.VerificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestTimeConfig.class)
@Transactional
class ReleaseExecutionServiceTest {

    @Autowired
    private ReleaseExecutionService releaseExecutionService;

    @Autowired
    private DisclosureService disclosureService;

    @Autowired
    private EstateService estateService;

    @Autowired
    private WillStateRepository willStateRepository;

    @Autowired
    private WillStateService willStateService;

    @Autowired
    private ReleaseExecutionRepository executionRepository;

    @Autowired
    private com.digitalwill.release.repository.ReleaseExecutionItemRepository executionItemRepository;

    @Autowired
    private com.digitalwill.release.repository.DisclosedRecordRepository disclosedRecordRepository;

    @Autowired
    private DisclosureTokenRepository disclosureTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private TestTimeProvider testTimeProvider;

    private final Instant baseTime = Instant.parse("2026-10-01T12:00:00Z");
    private UUID willId;
    private UUID ownerId;
    private Beneficiary benA;
    private Beneficiary benB;

    @BeforeEach
    void setUp() {
        testTimeProvider.setNow(baseTime);
        auditLogRepository.deleteAll();

        ownerId = UUID.randomUUID();
        willId = UUID.randomUUID();

        // Will in RELEASE_PENDING with release delay passed 10 minutes ago
        Instant releaseAfter = baseTime.minus(Duration.ofMinutes(10));
        WillStateEntity will = new WillStateEntity(willId, ownerId, "Release Test Will", WillState.RELEASE_PENDING, baseTime, baseTime);
        will.setReleaseAfter(releaseAfter);
        willStateRepository.save(will);

        // Add 2 beneficiaries
        benA = estateService.addBeneficiary(willId, "Alice Beneficiary", "alice@example.com", "Daughter");
        benB = estateService.addBeneficiary(willId, "Bob Beneficiary", "bob@example.com", "Son");

        // Add 2 assets
        Asset asset1 = estateService.addAsset(willId, "Main Residence", AssetCategory.REAL_ESTATE, "Family Home", null, "Key under mat");
        Asset asset2 = estateService.addAsset(willId, "Savings Account", AssetCategory.BANK_ACCOUNT, "Checking & Savings", null, "Contact branch manager");

        // Allocate: Alice gets 100% Home, Bob gets 100% Savings
        estateService.allocateAsset(asset1.getId(), benA.getId(), 100, "Maintain property in family");
        estateService.allocateAsset(asset2.getId(), benB.getId(), 100, "Funds for education");
    }

    @Test
    @DisplayName("Release execution atomically claims execution, generates scoped disclosure, and reaches EXECUTED")
    void releaseExecutionHappyPath() {
        ReleaseExecutionService.ExecutionResult result = releaseExecutionService.claimAndExecuteRelease(willId);

        assertThat(result.executed()).isTrue();
        assertThat(result.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(result.totalItems()).isEqualTo(2);
        assertThat(result.completedItems()).isEqualTo(2);
        assertThat(result.failedItems()).isEqualTo(0);

        // Will state is now terminal EXECUTED
        WillStateEntity will = willStateRepository.findById(willId).orElseThrow();
        assertThat(will.getState()).isEqualTo(WillState.EXECUTED);

        // Verify tokens created for both beneficiaries
        List<DisclosureToken> tokens = disclosureTokenRepository.findByWillId(willId);
        assertThat(tokens).hasSize(2);

        // Verify audit log has RELEASE_CLAIMED and RELEASE_EXECUTED
        List<AuditLogEntry> auditLogs = auditLogRepository.findByWillIdOrderBySequenceNumberAsc(willId);
        assertThat(auditLogs).anyMatch(e -> e.getAction() == AuditAction.RELEASE_CLAIMED);
        assertThat(auditLogs).anyMatch(e -> e.getAction() == AuditAction.RELEASE_EXECUTED);
    }

    @Test
    @DisplayName("Release execution is idempotent: running twice does not duplicate work")
    void releaseExecutionIsIdempotent() {
        ReleaseExecutionService.ExecutionResult first = releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(first.executed()).isTrue();

        ReleaseExecutionService.ExecutionResult second = releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(second.executed()).isTrue();
        assertThat(second.status()).isEqualTo(ExecutionStatus.COMPLETED);
    }

    @Test
    @DisplayName("Controlled disclosure: beneficiary can access only their allocated package with valid token")
    void controlledDisclosureAccess() {
        releaseExecutionService.claimAndExecuteRelease(willId);

        // Look up token for Alice
        DisclosureToken tokenAlice = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, benA.getId()).orElseThrow();

        // Generate matching raw token test setup
        String rawToken = tokenService.generateRawToken();
        tokenAlice.setTokenHash(tokenService.hashToken(rawToken));
        disclosureTokenRepository.save(tokenAlice);

        DisclosureService.BeneficiaryDisclosure disclosure = disclosureService.accessDisclosure(rawToken);

        assertThat(disclosure.willId()).isEqualTo(willId);
        assertThat(disclosure.beneficiaryId()).isEqualTo(benA.getId());
        assertThat(disclosure.payloadJson()).contains("Main Residence");
        assertThat(disclosure.payloadJson()).doesNotContain("Savings Account"); // Bob's asset is NOT leaked to Alice!

        // Token is now ACCESSED
        DisclosureToken updatedToken = disclosureTokenRepository.findById(tokenAlice.getId()).orElseThrow();
        assertThat(updatedToken.getStatus()).isEqualTo(DisclosureTokenStatus.ACCESSED);
        assertThat(updatedToken.getAccessCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Disclosure fails closed if token is expired")
    void disclosureFailsWhenTokenExpired() {
        releaseExecutionService.claimAndExecuteRelease(willId);

        DisclosureToken tokenAlice = disclosureTokenRepository.findByWillIdAndBeneficiaryId(willId, benA.getId()).orElseThrow();
        String rawToken = tokenService.generateRawToken();
        tokenAlice.setTokenHash(tokenService.hashToken(rawToken));
        tokenAlice.setExpiresAt(baseTime.minus(Duration.ofDays(1))); // expired yesterday
        disclosureTokenRepository.save(tokenAlice);

        assertThatThrownBy(() -> disclosureService.accessDisclosure(rawToken))
                .isInstanceOf(DisclosureTokenExpiredException.class);
    }

    @Test
    @DisplayName("Disclosure fails closed if Will is not yet EXECUTED")
    void disclosureFailsWhenWillNotExecuted() {
        // Will still in ACTIVE
        UUID activeWillId = UUID.randomUUID();
        WillStateEntity activeWill = new WillStateEntity(activeWillId, ownerId, "Active Will", WillState.ACTIVE, baseTime, baseTime);
        willStateRepository.save(activeWill);
        String rawToken = tokenService.generateRawToken();
        DisclosureToken token = new DisclosureToken(
                UUID.randomUUID(), activeWillId, benA.getId(), tokenService.hashToken(rawToken),
                DisclosureTokenStatus.ACTIVE, baseTime, baseTime.plus(Duration.ofDays(30))
        );
        disclosureTokenRepository.save(token);

        assertThatThrownBy(() -> disclosureService.accessDisclosure(rawToken))
                .isInstanceOf(DisclosureNotReadyException.class);
    }

    @Test
    @DisplayName("Crash recovery: items completed before crash are not duplicated on retry, remaining items complete, and state reaches EXECUTED")
    void crashRecoveryAndItemIdempotency() {
        // 1. Setup 3rd beneficiary Charlie with an asset
        Beneficiary benC = estateService.addBeneficiary(willId, "Charlie Beneficiary", "charlie@example.com", "Brother");
        Asset asset3 = estateService.addAsset(willId, "Classic Car", AssetCategory.VEHICLE, "1967 Mustang", null, "Garage keys");
        estateService.allocateAsset(asset3.getId(), benC.getId(), 100, "For Charlie");

        // 2. Worker claims execution: RELEASE_PENDING -> EXECUTING
        boolean claimed = willStateService.claimExecution(willId);
        assertThat(claimed).isTrue();
        WillStateEntity willExecuting = willStateRepository.findById(willId).orElseThrow();
        assertThat(willExecuting.getState()).isEqualTo(WillState.EXECUTING);

        // 3. Initialize execution record in progress
        Instant startTime = testTimeProvider.now();
        com.digitalwill.release.model.ReleaseExecution execution = new com.digitalwill.release.model.ReleaseExecution(
                UUID.randomUUID(), willId, ExecutionStatus.IN_PROGRESS, startTime
        );
        execution.setTotalItems(3);
        executionRepository.saveAndFlush(execution);

        // 4. Worker processes Item A and Item B successfully
        // Item A succeeds:
        String rawTokenA = tokenService.generateRawToken();
        DisclosureToken tokenA = new DisclosureToken(
                UUID.randomUUID(), willId, benA.getId(), tokenService.hashToken(rawTokenA),
                DisclosureTokenStatus.ACTIVE, startTime, startTime.plus(Duration.ofDays(30))
        );
        disclosureTokenRepository.save(tokenA);
        disclosedRecordRepository.save(new com.digitalwill.release.model.DisclosedRecord(
                UUID.randomUUID(), willId, benA.getId(), tokenA.getId(), "{\"asset\":\"Home\"}", startTime
        ));
        executionItemRepository.save(new com.digitalwill.release.model.ReleaseExecutionItem(
                UUID.randomUUID(), execution.getId(), "BENEFICIARY_DISCLOSURE",
                benA.getId(), com.digitalwill.release.model.ExecutionItemStatus.COMPLETED
        ));

        // Item B succeeds:
        String rawTokenB = tokenService.generateRawToken();
        DisclosureToken tokenB = new DisclosureToken(
                UUID.randomUUID(), willId, benB.getId(), tokenService.hashToken(rawTokenB),
                DisclosureTokenStatus.ACTIVE, startTime, startTime.plus(Duration.ofDays(30))
        );
        disclosureTokenRepository.save(tokenB);
        disclosedRecordRepository.save(new com.digitalwill.release.model.DisclosedRecord(
                UUID.randomUUID(), willId, benB.getId(), tokenB.getId(), "{\"asset\":\"Savings\"}", startTime
        ));
        executionItemRepository.save(new com.digitalwill.release.model.ReleaseExecutionItem(
                UUID.randomUUID(), execution.getId(), "BENEFICIARY_DISCLOSURE",
                benB.getId(), com.digitalwill.release.model.ExecutionItemStatus.COMPLETED
        ));

        // Item C is left pending (worker crashed before processing C or marking completion)
        executionItemRepository.save(new com.digitalwill.release.model.ReleaseExecutionItem(
                UUID.randomUUID(), execution.getId(), "BENEFICIARY_DISCLOSURE",
                benC.getId(), com.digitalwill.release.model.ExecutionItemStatus.PENDING
        ));

        // 5. Worker crashed: Will is still EXECUTING, execution is IN_PROGRESS
        WillStateEntity crashedWill = willStateRepository.findById(willId).orElseThrow();
        assertThat(crashedWill.getState()).isEqualTo(WillState.EXECUTING);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);

        // 6. Fast-forward clock past recovery timeout (e.g. 45 mins)
        Duration timeout = Duration.ofMinutes(30);
        testTimeProvider.setNow(startTime.plus(Duration.ofMinutes(45)));

        // 7. Trigger recovery: recoverStaleExecution
        ReleaseExecutionService.ExecutionResult recoveryResult =
                releaseExecutionService.recoverStaleExecution(willId, timeout);

        // 8. Verify recovery succeeded and completed remaining work
        assertThat(recoveryResult.executed()).isTrue();
        assertThat(recoveryResult.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(recoveryResult.totalItems()).isEqualTo(3);
        assertThat(recoveryResult.completedItems()).isEqualTo(3);
        assertThat(recoveryResult.failedItems()).isEqualTo(0);

        // 9. Verify state is terminal EXECUTED
        WillStateEntity finalWill = willStateRepository.findById(willId).orElseThrow();
        assertThat(finalWill.getState()).isEqualTo(WillState.EXECUTED);

        // 10. Verify items A and B were NOT duplicated (still exactly 1 token and 1 record per beneficiary)
        List<DisclosureToken> tokensAfter = disclosureTokenRepository.findByWillId(willId);
        assertThat(tokensAfter).hasSize(3); // Exactly 1 for A, 1 for B, 1 for C

        List<com.digitalwill.release.model.DisclosedRecord> recordsAfter =
                disclosedRecordRepository.findAll().stream().filter(r -> r.getWillId().equals(willId)).toList();
        assertThat(recordsAfter).hasSize(3); // Exactly 1 for A, 1 for B, 1 for C

        // 11. Another retry is completely harmless (no duplicates, returns EXECUTED)
        ReleaseExecutionService.ExecutionResult retryResult = releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(retryResult.executed()).isTrue();
        assertThat(retryResult.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(retryResult.message()).contains("already EXECUTED");

        List<DisclosureToken> tokensAfterSecondRetry = disclosureTokenRepository.findByWillId(willId);
        assertThat(tokensAfterSecondRetry).hasSize(3);
    }
}

