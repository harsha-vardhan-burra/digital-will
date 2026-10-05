package com.digitalwill.release.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.release.exception.DisclosureNotReadyException;
import com.digitalwill.release.exception.DisclosureTokenExpiredException;
import com.digitalwill.release.exception.DisclosureTokenInvalidException;
import com.digitalwill.release.exception.DisclosureTokenRevokedException;
import com.digitalwill.release.model.DisclosedRecord;
import com.digitalwill.release.model.DisclosureToken;
import com.digitalwill.release.model.DisclosureTokenStatus;
import com.digitalwill.release.repository.DisclosedRecordRepository;
import com.digitalwill.release.repository.DisclosureTokenRepository;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.verification.service.VerificationTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Service providing secure, state-aware controlled disclosure to authorized beneficiaries.
 */
@Service
public class DisclosureService {

    private static final Logger log = LoggerFactory.getLogger(DisclosureService.class);

    private final DisclosureTokenRepository disclosureTokenRepository;
    private final DisclosedRecordRepository disclosedRecordRepository;
    private final WillStateRepository willStateRepository;
    private final VerificationTokenService tokenService;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public DisclosureService(DisclosureTokenRepository disclosureTokenRepository,
                             DisclosedRecordRepository disclosedRecordRepository,
                             WillStateRepository willStateRepository,
                             VerificationTokenService tokenService,
                             AuditLogService auditLogService,
                             TimeProvider timeProvider) {
        this.disclosureTokenRepository = Objects.requireNonNull(disclosureTokenRepository);
        this.disclosedRecordRepository = Objects.requireNonNull(disclosedRecordRepository);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

    public record BeneficiaryDisclosure(
            UUID willId,
            UUID beneficiaryId,
            String payloadJson,
            Instant accessedAt
    ) {}

    @Transactional
    public BeneficiaryDisclosure accessDisclosure(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DisclosureTokenInvalidException("Disclosure token must not be blank");
        }

        String tokenHash = tokenService.hashToken(rawToken.trim());
        DisclosureToken token = disclosureTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new DisclosureTokenInvalidException("Invalid disclosure token"));

        Instant now = timeProvider.now();

        // 1. Status checks
        if (token.getStatus() == DisclosureTokenStatus.REVOKED) {
            throw new DisclosureTokenRevokedException("Disclosure token has been revoked");
        }
        if (token.getStatus() == DisclosureTokenStatus.EXPIRED || now.isAfter(token.getExpiresAt())) {
            token.setStatus(DisclosureTokenStatus.EXPIRED);
            disclosureTokenRepository.save(token);
            throw new DisclosureTokenExpiredException("Disclosure token has expired");
        }

        // 2. Will state check - must be terminal EXECUTED
        WillStateEntity will = willStateRepository.findById(token.getWillId())
                .orElseThrow(() -> new IllegalArgumentException("Associated will not found: " + token.getWillId()));

        if (will.getState() != WillState.EXECUTED) {
            throw new DisclosureNotReadyException("Estate disclosure is not ready: will state is " + will.getState());
        }

        // 3. Update access telemetry
        if (token.getFirstAccessedAt() == null) {
            token.setFirstAccessedAt(now);
        }
        token.setLastAccessedAt(now);
        token.setAccessCount(token.getAccessCount() + 1);
        token.setStatus(DisclosureTokenStatus.ACCESSED);
        disclosureTokenRepository.save(token);

        // 4. Fetch disclosed record
        DisclosedRecord record = disclosedRecordRepository.findByWillIdAndBeneficiaryId(token.getWillId(), token.getBeneficiaryId())
                .orElseThrow(() -> new IllegalStateException("Disclosed record not found for beneficiary: " + token.getBeneficiaryId()));

        // 5. Audit access event
        auditLogService.logCritical(
                token.getWillId(),
                token.getBeneficiaryId().toString(),
                "BENEFICIARY",
                AuditAction.DISCLOSURE_ACCESSED,
                AuditStatus.SUCCESS,
                AuditResourceType.DISCLOSURE,
                record.getId().toString(),
                "{\"accessCount\":" + token.getAccessCount() + "}"
        );

        log.info("Beneficiary [{}] accessed disclosure package for Will [{}]", token.getBeneficiaryId(), token.getWillId());
        return new BeneficiaryDisclosure(token.getWillId(), token.getBeneficiaryId(), record.getPackagePayloadJson(), now);
    }

    /**
     * Atomically consumes a disclosure token for single-use access.
     * Prevents replay and concurrent double-consumption.
     */
    @Transactional
    public BeneficiaryDisclosure consumeDisclosure(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DisclosureTokenInvalidException("Disclosure token must not be blank");
        }

        String tokenHash = tokenService.hashToken(rawToken.trim());
        DisclosureToken token = disclosureTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new DisclosureTokenInvalidException("Invalid disclosure token"));

        Instant now = timeProvider.now();

        // 1. Status checks
        if (token.getStatus() == DisclosureTokenStatus.REVOKED) {
            throw new com.digitalwill.release.exception.DisclosureTokenRevokedException("Disclosure token has been revoked");
        }
        if (token.getStatus() == DisclosureTokenStatus.EXPIRED || now.isAfter(token.getExpiresAt())) {
            token.setStatus(DisclosureTokenStatus.EXPIRED);
            disclosureTokenRepository.save(token);
            throw new DisclosureTokenExpiredException("Disclosure token has expired");
        }
        if (token.getStatus() == DisclosureTokenStatus.ACCESSED) {
            throw new com.digitalwill.release.exception.DisclosureTokenConsumedException("Disclosure token has already been consumed");
        }

        // 2. Will state check - must be terminal EXECUTED
        WillStateEntity will = willStateRepository.findById(token.getWillId())
                .orElseThrow(() -> new IllegalArgumentException("Associated will not found: " + token.getWillId()));

        if (will.getState() != WillState.EXECUTED) {
            throw new DisclosureNotReadyException("Estate disclosure is not ready: will state is " + will.getState());
        }

        // 3. Atomic single-use consumption claim
        int updated = disclosureTokenRepository.consumeTokenIfActive(token.getId(), now);
        if (updated == 0) {
            throw new com.digitalwill.release.exception.DisclosureTokenConsumedException("Disclosure token already consumed or concurrent consumption detected");
        }

        // 4. Fetch disclosed record
        DisclosedRecord record = disclosedRecordRepository.findByWillIdAndBeneficiaryId(token.getWillId(), token.getBeneficiaryId())
                .orElseThrow(() -> new IllegalStateException("Disclosed record not found for beneficiary: " + token.getBeneficiaryId()));

        // 5. Audit single-use access event
        auditLogService.logCritical(
                token.getWillId(),
                token.getBeneficiaryId().toString(),
                "BENEFICIARY",
                AuditAction.DISCLOSURE_ACCESSED,
                AuditStatus.SUCCESS,
                AuditResourceType.DISCLOSURE,
                record.getId().toString(),
                "{\"consumed\":true}"
        );

        log.info("Beneficiary [{}] consumed single-use disclosure token for Will [{}]", token.getBeneficiaryId(), token.getWillId());
        return new BeneficiaryDisclosure(token.getWillId(), token.getBeneficiaryId(), record.getPackagePayloadJson(), now);
    }
}
