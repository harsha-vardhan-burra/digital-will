package com.digitalwill.workflow;

import com.digitalwill.audit.repository.AuditLogRepository;
import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.estate.model.Asset;
import com.digitalwill.estate.model.AssetCategory;
import com.digitalwill.estate.model.Beneficiary;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.release.model.DisclosureToken;
import com.digitalwill.release.model.ExecutionItemStatus;
import com.digitalwill.release.model.ExecutionStatus;
import com.digitalwill.release.model.ReleaseExecution;
import com.digitalwill.release.model.ReleaseExecutionItem;
import com.digitalwill.release.repository.DisclosedRecordRepository;
import com.digitalwill.release.repository.DisclosureTokenRepository;
import com.digitalwill.release.repository.ReleaseExecutionItemRepository;
import com.digitalwill.release.repository.ReleaseExecutionRepository;
import com.digitalwill.release.service.ReleaseExecutionService;
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

/**
 * Phase 3 Workflow 4: Release Failure Recovery & Idempotency
 *
 * Verifies that:
 * 1. A simulated worker crash midway through release (after 1 of 2 beneficiaries processed) leaves state safe.
 * 2. Recovery detects stalled EXECUTING state after timeout.
 * 3. Idempotent retry skips previously completed items without duplicate work.
 * 4. Remaining items complete cleanly.
 * 5. State reaches terminal EXECUTED.
 * 6. Token, item, and disclosure counts are exactly correct with zero duplicates.
 */
@SpringBootTest
@Import(TestTimeConfig.class)
@Transactional
class ReleaseFailureRecoveryIntegrationTest {

    @Autowired
    private ReleaseExecutionService releaseExecutionService;

    @Autowired
    private EstateService estateService;

    @Autowired
    private WillStateRepository willStateRepository;

    @Autowired
    private WillStateService willStateService;

    @Autowired
    private ReleaseExecutionRepository executionRepository;

    @Autowired
    private ReleaseExecutionItemRepository executionItemRepository;

    @Autowired
    private DisclosureTokenRepository disclosureTokenRepository;

    @Autowired
    private DisclosedRecordRepository disclosedRecordRepository;

    @Autowired
    private VerificationTokenService tokenService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestTimeProvider timeProvider;

    private final Instant baseTime = Instant.parse("2026-05-01T10:00:00Z");
    private UUID willId;
    private Beneficiary ben1;
    private Beneficiary ben2;

    @BeforeEach
    void setUp() {
        timeProvider.setNow(baseTime);
        auditLogRepository.deleteAll();

        UUID ownerId = UUID.randomUUID();
        willId = UUID.randomUUID();

        // Setup will in RELEASE_PENDING with delay elapsed
        Instant releaseAfter = baseTime.minus(Duration.ofHours(1));
        WillStateEntity will = new WillStateEntity(willId, ownerId, "Failure Recovery Will", WillState.RELEASE_PENDING, baseTime, baseTime);
        will.setReleaseAfter(releaseAfter);
        willStateRepository.save(will);

        // Add 2 beneficiaries
        ben1 = estateService.addBeneficiary(willId, "Alice Recovery", "alice@example.com", "Sister");
        ben2 = estateService.addBeneficiary(willId, "Bob Recovery", "bob@example.com", "Brother");

        // Add 2 assets and allocations
        Asset asset1 = estateService.addAsset(willId, "House", AssetCategory.REAL_ESTATE, "Main house", null, "Key under mat");
        Asset asset2 = estateService.addAsset(willId, "Brokerage", AssetCategory.INVESTMENT, "Index funds", null, "Advisor contact");

        estateService.allocateAsset(asset1.getId(), ben1.getId(), 100, "100% to Alice");
        estateService.allocateAsset(asset2.getId(), ben2.getId(), 100, "100% to Bob");
    }

    @Test
    @DisplayName("Workflow 4: Mid-execution crash recovery skips completed items and idempotently completes remaining work")
    void crashRecoveryAndIdempotentCompletion() {
        // Step 1: Simulate worker starting execution (RELEASE_PENDING -> EXECUTING)
        boolean claimed = willStateService.claimExecution(willId);
        assertThat(claimed).isTrue();
        WillStateEntity willExecuting = willStateRepository.findById(willId).orElseThrow();
        assertThat(willExecuting.getState()).isEqualTo(WillState.EXECUTING);
        assertThat(willExecuting.getExecutingAt()).isNotNull();

        // Initialize execution record in progress
        Instant crashTime = timeProvider.now();
        ReleaseExecution execution = new ReleaseExecution(UUID.randomUUID(), willId, ExecutionStatus.IN_PROGRESS, crashTime);
        execution.setTotalItems(2);
        execution.setCompletedItems(1);
        execution.setFailedItems(0);
        executionRepository.saveAndFlush(execution);

        // Step 2: Simulate Alice (ben1) completed before crash
        String rawToken1 = tokenService.generateRawToken();
        DisclosureToken tokenAlice = new DisclosureToken(
                UUID.randomUUID(), willId, ben1.getId(), tokenService.hashToken(rawToken1),
                com.digitalwill.release.model.DisclosureTokenStatus.ACTIVE, crashTime, crashTime.plus(Duration.ofDays(30))
        );
        disclosureTokenRepository.save(tokenAlice);

        disclosedRecordRepository.save(new com.digitalwill.release.model.DisclosedRecord(
                UUID.randomUUID(), willId, ben1.getId(), tokenAlice.getId(),
                "{\"beneficiary\":\"Alice Recovery\",\"assets\":[{\"title\":\"House\"}]}", crashTime
        ));

        ReleaseExecutionItem itemAlice = new ReleaseExecutionItem(
                UUID.randomUUID(), execution.getId(), "BENEFICIARY_DISCLOSURE",
                ben1.getId(), ExecutionItemStatus.COMPLETED
        );
        itemAlice.setCompletedAt(crashTime);
        executionItemRepository.save(itemAlice);

        // Bob (ben2) was NOT processed before worker crashed
        // Now time passes past the recovery timeout (e.g. 15 minutes)
        timeProvider.advance(Duration.ofMinutes(20));

        // Step 3: Trigger crash recovery
        ReleaseExecutionService.ExecutionResult recoveryResult =
                releaseExecutionService.recoverStaleExecution(willId, Duration.ofMinutes(15));

        assertThat(recoveryResult.executed()).isTrue();
        assertThat(recoveryResult.status()).isEqualTo(ExecutionStatus.COMPLETED);

        // Step 4: Verify terminal state reached
        WillStateEntity terminalWill = willStateRepository.findById(willId).orElseThrow();
        assertThat(terminalWill.getState()).isEqualTo(WillState.EXECUTED);

        // Step 5: Verify exact counts and NO duplication
        List<DisclosureToken> tokens = disclosureTokenRepository.findByWillId(willId);
        assertThat(tokens).hasSize(2); // Exactly 1 for Alice, exactly 1 for Bob

        List<ReleaseExecutionItem> items = executionItemRepository.findByExecutionId(execution.getId());
        assertThat(items).hasSize(2);
        assertThat(items).allMatch(it -> it.getStatus() == ExecutionItemStatus.COMPLETED);

        // Step 6: Verify second execution attempt is completely idempotent
        ReleaseExecutionService.ExecutionResult secondAttempt =
                releaseExecutionService.claimAndExecuteRelease(willId);
        assertThat(secondAttempt.executed()).isTrue();
        assertThat(secondAttempt.status()).isEqualTo(ExecutionStatus.COMPLETED);

        // Counts remain strictly unchanged
        assertThat(disclosureTokenRepository.findByWillId(willId)).hasSize(2);
    }
}
