package com.digitalwill.job.service;

import com.digitalwill.common.TestTimeProvider;
import com.digitalwill.config.TestTimeConfig;
import com.digitalwill.estate.service.EstateService;
import com.digitalwill.job.config.JobProperties;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.verification.model.TrustedContact;
import com.digitalwill.verification.model.VerificationRequest;
import com.digitalwill.verification.repository.VerificationRequestRepository;
import com.digitalwill.verification.service.VerificationService;
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

@SpringBootTest
@Import(TestTimeConfig.class)
@Transactional
class InactivityProcessingJobTest {

    @Autowired
    private InactivityProcessingJobService inactivityJobService;

    @Autowired
    private WillStateRepository willStateRepository;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private VerificationRequestRepository verificationRequestRepository;

    @Autowired
    private TestTimeProvider testTimeProvider;

    @Autowired
    private JobProperties jobProperties;

    private final Instant baseTime = Instant.parse("2026-10-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        testTimeProvider.setNow(baseTime);
    }

    @Test
    @DisplayName("Inactivity job advances eligible wills across lifecycle stages without stage-skipping")
    void inactivityJobAdvancesEligibleWills() {
        // Will 1: ACTIVE with activity 95 days ago -> should advance to INACTIVITY_WARNING
        UUID will1Id = UUID.randomUUID();
        WillStateEntity will1 = new WillStateEntity(
                will1Id, UUID.randomUUID(), "Will 1", WillState.ACTIVE,
                baseTime.minus(Duration.ofDays(100)), baseTime.minus(Duration.ofDays(95))
        );
        willStateRepository.save(will1);

        // Will 2: INACTIVITY_WARNING with warning sent 15 days ago -> should advance to FINAL_WARNING
        UUID will2Id = UUID.randomUUID();
        WillStateEntity will2 = new WillStateEntity(
                will2Id, UUID.randomUUID(), "Will 2", WillState.INACTIVITY_WARNING,
                baseTime.minus(Duration.ofDays(110)), baseTime.minus(Duration.ofDays(105))
        );
        will2.setWarningSentAt(baseTime.minus(Duration.ofDays(15)));
        willStateRepository.save(will2);

        // Will 3: FINAL_WARNING with final warning sent 8 days ago and trusted contact -> should advance to VERIFICATION_PENDING
        UUID will3Id = UUID.randomUUID();
        WillStateEntity will3 = new WillStateEntity(
                will3Id, UUID.randomUUID(), "Will 3", WillState.FINAL_WARNING,
                baseTime.minus(Duration.ofDays(120)), baseTime.minus(Duration.ofDays(115))
        );
        will3.setFinalWarningSentAt(baseTime.minus(Duration.ofDays(8)));
        willStateRepository.save(will3);

        TrustedContact contact = verificationService.createTrustedContact("Jane Contact", "jane@example.com");
        verificationService.associateContactWithWill(will3Id, contact.getId());

        // Will 4: ACTIVE with recent activity (1 day ago) -> should remain ACTIVE
        UUID will4Id = UUID.randomUUID();
        WillStateEntity will4 = new WillStateEntity(
                will4Id, UUID.randomUUID(), "Will 4 Recent", WillState.ACTIVE,
                baseTime.minus(Duration.ofDays(10)), baseTime.minus(Duration.ofDays(1))
        );
        willStateRepository.save(will4);

        // Run background job
        InactivityProcessingJobService.InactivityJobReport report = inactivityJobService.processInactivityJob();

        assertThat(report.warnedActiveWills()).isGreaterThanOrEqualTo(1);
        assertThat(report.finalWarnedWills()).isGreaterThanOrEqualTo(1);
        assertThat(report.initiatedVerifications()).isGreaterThanOrEqualTo(1);
        assertThat(report.errors()).isEmpty();

        // Verify resulting states
        assertThat(willStateRepository.findById(will1Id).orElseThrow().getState()).isEqualTo(WillState.INACTIVITY_WARNING);
        assertThat(willStateRepository.findById(will2Id).orElseThrow().getState()).isEqualTo(WillState.FINAL_WARNING);
        assertThat(willStateRepository.findById(will3Id).orElseThrow().getState()).isEqualTo(WillState.VERIFICATION_PENDING);
        assertThat(willStateRepository.findById(will4Id).orElseThrow().getState()).isEqualTo(WillState.ACTIVE);

        // Verify verification request token was created for Will 3's contact
        List<VerificationRequest> requests = verificationRequestRepository.findByWillId(will3Id);
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getContactId()).isEqualTo(contact.getId());
    }

    @Test
    @DisplayName("Inactivity job is idempotent: running twice in immediate succession produces 0 additional transitions")
    void inactivityJobIsIdempotent() {
        UUID willId = UUID.randomUUID();
        WillStateEntity will = new WillStateEntity(
                willId, UUID.randomUUID(), "Idempotent Will", WillState.ACTIVE,
                baseTime.minus(Duration.ofDays(100)), baseTime.minus(Duration.ofDays(95))
        );
        willStateRepository.save(will);

        InactivityProcessingJobService.InactivityJobReport first = inactivityJobService.processInactivityJob();
        assertThat(first.warnedActiveWills()).isGreaterThanOrEqualTo(1);

        InactivityProcessingJobService.InactivityJobReport second = inactivityJobService.processInactivityJob();
        assertThat(second.warnedActiveWills()).isEqualTo(0);
        assertThat(second.finalWarnedWills()).isEqualTo(0);
        assertThat(second.initiatedVerifications()).isEqualTo(0);
    }
}
