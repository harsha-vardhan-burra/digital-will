package com.digitalwill.verification.service;

import com.digitalwill.common.TimeProvider;
import com.digitalwill.state.model.WillState;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.state.service.WillStateService;
import com.digitalwill.verification.exception.AlreadyConfirmedException;
import com.digitalwill.verification.exception.InvalidVerificationStateException;
import com.digitalwill.verification.exception.InvalidVerificationTokenException;
import com.digitalwill.verification.exception.VerificationRequestRevokedException;
import com.digitalwill.verification.exception.VerificationTokenAlreadyUsedException;
import com.digitalwill.verification.exception.VerificationTokenExpiredException;
import com.digitalwill.verification.model.ContactConfirmation;
import com.digitalwill.verification.model.TrustedContact;
import com.digitalwill.verification.model.VerificationRequest;
import com.digitalwill.verification.model.VerificationRequestStatus;
import com.digitalwill.verification.model.WillContact;
import com.digitalwill.verification.repository.ContactConfirmationRepository;
import com.digitalwill.verification.repository.TrustedContactRepository;
import com.digitalwill.verification.repository.VerificationRequestRepository;
import com.digitalwill.verification.repository.WillContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofDays(7);
    private static final int REQUIRED_CONFIRMATIONS = 2;

    private final TrustedContactRepository trustedContactRepository;
    private final WillContactRepository willContactRepository;
    private final VerificationRequestRepository verificationRequestRepository;
    private final ContactConfirmationRepository confirmationRepository;
    private final WillStateRepository willStateRepository;
    private final WillStateService willStateService;
    private final VerificationTokenService tokenService;
    private final TimeProvider timeProvider;
    private final com.digitalwill.notification.service.NotificationDeliveryService notificationDeliveryService;

    @org.springframework.beans.factory.annotation.Autowired
    public VerificationService(TrustedContactRepository trustedContactRepository,
                               WillContactRepository willContactRepository,
                               VerificationRequestRepository verificationRequestRepository,
                               ContactConfirmationRepository confirmationRepository,
                               WillStateRepository willStateRepository,
                               WillStateService willStateService,
                               VerificationTokenService tokenService,
                               TimeProvider timeProvider,
                               com.digitalwill.notification.service.NotificationDeliveryService notificationDeliveryService) {
        this.trustedContactRepository = Objects.requireNonNull(trustedContactRepository);
        this.willContactRepository = Objects.requireNonNull(willContactRepository);
        this.verificationRequestRepository = Objects.requireNonNull(verificationRequestRepository);
        this.confirmationRepository = Objects.requireNonNull(confirmationRepository);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.willStateService = Objects.requireNonNull(willStateService);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
        this.notificationDeliveryService = notificationDeliveryService;
    }

    public VerificationService(TrustedContactRepository trustedContactRepository,
                               WillContactRepository willContactRepository,
                               VerificationRequestRepository verificationRequestRepository,
                               ContactConfirmationRepository confirmationRepository,
                               WillStateRepository willStateRepository,
                               WillStateService willStateService,
                               VerificationTokenService tokenService,
                               TimeProvider timeProvider) {
        this(trustedContactRepository, willContactRepository, verificationRequestRepository,
             confirmationRepository, willStateRepository, willStateService, tokenService, timeProvider, null);
    }

    // ---- Trusted Contact management ----

    public record WillContactDetailed(
            UUID associationId,
            UUID contactId,
            UUID willId,
            String name,
            String email,
            boolean isActive,
            Instant addedAt,
            boolean confirmedCurrentCycle
    ) {}

    @Transactional
    public WillContactDetailed addTrustedContactToWill(UUID willId, String name, String email) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Contact name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Contact email must not be blank");
        }
        String cleanEmail = email.trim().toLowerCase();
        TrustedContact contact = trustedContactRepository.findByEmail(cleanEmail)
                .orElseGet(() -> createTrustedContact(name.trim(), cleanEmail));

        WillContact wc = willContactRepository.findByWillIdAndContactId(willId, contact.getId())
                .orElseGet(() -> {
                    WillContact newWc = new WillContact(UUID.randomUUID(), willId, contact.getId(), timeProvider.now());
                    return willContactRepository.saveAndFlush(newWc);
                });

        if (!wc.isActive()) {
            wc.setActive(true);
            wc = willContactRepository.saveAndFlush(wc);
        }

        return new WillContactDetailed(wc.getId(), contact.getId(), willId, contact.getName(), contact.getEmail(), wc.isActive(), wc.getCreatedAt(), false);
    }

    @Transactional
    public void deactivateContactForWill(UUID willId, UUID contactId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found: " + willId));
        if (will.getState() != WillState.ACTIVE) {
            throw new IllegalStateException("Contacts can only be deactivated when Will is in ACTIVE state");
        }
        WillContact wc = willContactRepository.findByWillIdAndContactId(willId, contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact association not found"));
        wc.setActive(false);
        willContactRepository.saveAndFlush(wc);
    }

    public List<WillContactDetailed> listContactsForWillDetailed(UUID willId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found: " + willId));
        Long cycle = will.getVerificationCycle() != null ? will.getVerificationCycle() : 0L;

        List<WillContact> willContacts = willContactRepository.findByWillId(willId);
        return willContacts.stream().map(wc -> {
            TrustedContact tc = trustedContactRepository.findById(wc.getContactId()).orElse(null);
            String name = tc != null ? tc.getName() : "Unknown";
            String email = tc != null ? tc.getEmail() : "";
            boolean confirmed = confirmationRepository.existsByWillIdAndContactIdAndVerificationCycle(willId, wc.getContactId(), cycle);
            return new WillContactDetailed(wc.getId(), wc.getContactId(), willId, name, email, wc.isActive(), wc.getCreatedAt(), confirmed);
        }).toList();
    }

    @Transactional
    public TrustedContact createTrustedContact(String name, String email) {
        Instant now = timeProvider.now();
        TrustedContact contact = new TrustedContact(UUID.randomUUID(), name, email, now);
        return trustedContactRepository.saveAndFlush(contact);
    }

    @Transactional
    public WillContact associateContactWithWill(UUID willId, UUID contactId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found: " + willId));
        trustedContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));
        if (willContactRepository.existsByWillIdAndContactId(willId, contactId)) {
            return willContactRepository.findByWillIdAndContactId(willId, contactId).orElseThrow();
        }
        WillContact wc = new WillContact(UUID.randomUUID(), willId, contactId, timeProvider.now());
        return willContactRepository.saveAndFlush(wc);
    }

    public List<WillContact> listContactsForWill(UUID willId) {
        return willContactRepository.findByWillId(willId);
    }

    // ---- Verification Request creation ----

    /**
     * Creates a verification request for a specific contact and will.
     * Returns raw token (to be emailed). Only hash is persisted.
     */
    @Transactional
    public String createVerificationRequest(UUID willId, UUID contactId, Duration ttl) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found: " + willId));
        if (will.getState() != WillState.VERIFICATION_PENDING) {
            throw new InvalidVerificationStateException("Will must be VERIFICATION_PENDING to create verification request, current: " + will.getState());
        }
        WillContact wc = willContactRepository.findByWillIdAndContactId(willId, contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not associated with will"));
        if (!wc.isActive()) {
            throw new InvalidVerificationStateException("Contact association is inactive");
        }
        Instant now = timeProvider.now();
        Duration effectiveTtl = ttl != null ? ttl : DEFAULT_TOKEN_TTL;
        Instant expiresAt = now.plus(effectiveTtl);

        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);
        Long cycle = will.getVerificationCycle() != null ? will.getVerificationCycle() : 0L;

        VerificationRequest req = new VerificationRequest(UUID.randomUUID(), willId, contactId, tokenHash, cycle, now, expiresAt);
        verificationRequestRepository.save(req);
        log.info("Created verification request [{}] for will [{}] contact [{}] cycle [{}] expiresAt [{}]",
                req.getId(), willId, contactId, cycle, expiresAt);

        if (notificationDeliveryService != null) {
            trustedContactRepository.findById(contactId).ifPresent(contact -> {
                notificationDeliveryService.sendVerificationNotification(contact.getEmail(), contact.getName(), willId, rawToken);
            });
        }

        return rawToken;
    }

    public String createVerificationRequest(UUID willId, UUID contactId) {
        return createVerificationRequest(willId, contactId, DEFAULT_TOKEN_TTL);
    }

    // ---- Confirmation ----

    public record ConfirmationResult(boolean confirmed, boolean quorumReached, boolean alreadyConfirmed, WillState resultingState) {}

    /**
     * Confirms via raw token. Validates expiry (TimeProvider), status, cycle, Will state, duplicate.
     * Atomically records confirmation and triggers State Engine if quorum reached.
     */
    @Transactional
    public ConfirmationResult confirm(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidVerificationTokenException("Token must not be blank");
        }
        String tokenHash = tokenService.hashToken(rawToken);
        VerificationRequest request = verificationRequestRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid verification token"));

        Instant now = timeProvider.now();

        // Status checks
        if (request.getStatus() == VerificationRequestStatus.REVOKED) {
            throw new VerificationRequestRevokedException("Verification request has been revoked");
        }
        if (request.getStatus() == VerificationRequestStatus.CONFIRMED) {
            throw new VerificationTokenAlreadyUsedException("Verification token already used");
        }
        if (request.getStatus() == VerificationRequestStatus.EXPIRED) {
            throw new VerificationTokenExpiredException("Verification token expired");
        }
        // Expiry: TimeProvider based
        if (now.isAfter(request.getExpiresAt()) || now.equals(request.getExpiresAt())) {
            // Mark expired atomically if still ACTIVE
            request.setStatus(VerificationRequestStatus.EXPIRED);
            verificationRequestRepository.save(request);
            throw new VerificationTokenExpiredException("Verification token expired");
        }
        if (request.getStatus() != VerificationRequestStatus.ACTIVE) {
            throw new InvalidVerificationTokenException("Verification request not active");
        }

        WillStateEntity will = willStateRepository.findByIdForUpdate(request.getWillId())
                .orElseThrow(() -> new IllegalArgumentException("Will not found: " + request.getWillId()));

        // Cycle safety: token cycle must equal current will cycle
        Long willCycle = will.getVerificationCycle() != null ? will.getVerificationCycle() : 0L;
        if (!willCycle.equals(request.getVerificationCycle())) {
            throw new VerificationRequestRevokedException("Verification request belongs to stale cycle");
        }

        // Will must be in VERIFICATION_PENDING (or already VERIFIED in current cycle, Sec 42.3)
        if (will.getState() != WillState.VERIFICATION_PENDING && will.getState() != WillState.VERIFIED) {
            throw new InvalidVerificationStateException("Will not in verifiable state, current: " + will.getState());
        }

        // WillContact must exist and active
        WillContact wc = willContactRepository.findByWillIdAndContactId(request.getWillId(), request.getContactId())
                .orElseThrow(() -> new InvalidVerificationTokenException("Contact not associated with will"));
        if (!wc.isActive()) {
            throw new VerificationRequestRevokedException("Contact association revoked");
        }

        // Distinct-contact invariant: check if this contact already confirmed this cycle
        if (confirmationRepository.existsByWillIdAndContactIdAndVerificationCycle(
                request.getWillId(), request.getContactId(), request.getVerificationCycle())) {
            // Idempotent: token consumption - return already-confirmed without creating duplicate
            // But still consume token to prevent reuse? Per spec single-use: consume after success.
            // If already confirmed via different request/token, this duplicate token should be rejected as AlreadyConfirmed
            throw new AlreadyConfirmedException("Contact has already confirmed this verification cycle");
        }

        // Atomic confirmation insert (DB unique constraint is final guard)
        ContactConfirmation confirmation = new ContactConfirmation(
                UUID.randomUUID(),
                request.getWillId(),
                request.getContactId(),
                request.getId(),
                request.getVerificationCycle(),
                now);
        try {
            confirmationRepository.saveAndFlush(confirmation);
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate: unique (will_id, contact_id, cycle) violated
            throw new AlreadyConfirmedException("Contact has already confirmed this verification cycle (concurrent)");
        }

        // Consume token
        request.setStatus(VerificationRequestStatus.CONFIRMED);
        request.setConsumedAt(now);
        verificationRequestRepository.save(request);

        long distinctCount = confirmationRepository.countDistinctContactsByWillIdAndCycle(request.getWillId(), request.getVerificationCycle());
        log.info("Confirmation recorded for will [{}] contact [{}] cycle [{}] distinctCount [{}]",
                request.getWillId(), request.getContactId(), request.getVerificationCycle(), distinctCount);

        boolean quorumReached = false;
        if (distinctCount >= REQUIRED_CONFIRMATIONS) {
            if (will.getState() == WillState.VERIFICATION_PENDING) {
                // Integrate with State Engine - authoritative transition VERIFICATION_PENDING -> VERIFIED
                // State Engine remains atomic via conditional UPDATE
                boolean verified = willStateService.triggerVerified(request.getWillId(), (int) distinctCount, REQUIRED_CONFIRMATIONS);
                quorumReached = verified;
                if (verified) {
                    log.info("Quorum reached (2-of-3) for will [{}] cycle [{}], transitioned to VERIFIED", request.getWillId(), request.getVerificationCycle());
                } else {
                    // Another concurrent quorum already transitioned - safe idempotent
                    log.info("Quorum count satisfied but state already transitioned for will [{}]", request.getWillId());
                    quorumReached = true;
                }
            } else {
                // Already in VERIFIED state in this cycle - no secondary transition permitted (Sec 42.3)
                quorumReached = true;
            }
        }

        WillState resultingState = willStateRepository.findById(request.getWillId()).map(WillStateEntity::getState).orElse(will.getState());
        return new ConfirmationResult(true, quorumReached, false, resultingState);
    }

    /**
     * Idempotent wrapper: if token already CONFIRMED, returns quorum status without error where appropriate.
     * For spec idempotency: second submission of same token returns deterministic result.
     * We treat AlreadyConfirmed/AlreadyUsed as idempotent confirmed response when confirmation exists.
     */
    @Transactional
    public ConfirmationResult confirmIdempotent(String rawToken) {
        try {
            return confirm(rawToken);
        } catch (VerificationTokenAlreadyUsedException | AlreadyConfirmedException e) {
            // Lookup request to return idempotent state
            String hash = tokenService.hashToken(rawToken);
            VerificationRequest req = verificationRequestRepository.findByTokenHash(hash).orElseThrow(() -> e);
            Long cycle = req.getVerificationCycle();
            long distinctCount = confirmationRepository.countDistinctContactsByWillIdAndCycle(req.getWillId(), cycle);
            WillState state = willStateRepository.findById(req.getWillId()).map(WillStateEntity::getState).orElse(WillState.VERIFICATION_PENDING);
            boolean quorum = distinctCount >= REQUIRED_CONFIRMATIONS || state == WillState.VERIFIED;
            return new ConfirmationResult(true, quorum, true, state);
        }
    }

    // ---- Reset / cycle invalidation ----

    /**
     * Increments verification cycle and revokes ACTIVE requests. To be called after owner reset.
     * Also used by WillStateService integration.
     */
    @Transactional
    public void invalidateVerificationForWill(UUID willId) {
        WillStateEntity will = willStateRepository.findById(willId)
                .orElseThrow(() -> new IllegalArgumentException("Will not found: " + willId));
        long nextCycle = (will.getVerificationCycle() != null ? will.getVerificationCycle() : 0L) + 1;
        will.setVerificationCycle(nextCycle);
        willStateRepository.save(will);
        verificationRequestRepository.revokeActiveByWillId(willId);
        log.info("Invalidated verification for will [{}], new cycle [{}]", willId, nextCycle);
    }

    public long countDistinctConfirmations(UUID willId) {
        WillStateEntity will = willStateRepository.findById(willId).orElseThrow();
        Long cycle = will.getVerificationCycle() != null ? will.getVerificationCycle() : 0L;
        return confirmationRepository.countDistinctContactsByWillIdAndCycle(willId, cycle);
    }

    public int getRequiredConfirmations() { return REQUIRED_CONFIRMATIONS; }
    public Duration getDefaultTokenTtl() { return DEFAULT_TOKEN_TTL; }
}