package com.digitalwill.auth.service;

import com.digitalwill.audit.model.AuditAction;
import com.digitalwill.audit.model.AuditResourceType;
import com.digitalwill.audit.model.AuditStatus;
import com.digitalwill.audit.service.AuditLogService;
import com.digitalwill.auth.model.User;
import com.digitalwill.auth.model.UserAuthToken;
import com.digitalwill.auth.repository.UserAuthTokenRepository;
import com.digitalwill.auth.repository.UserRepository;
import com.digitalwill.auth.security.UserPrincipal;
import com.digitalwill.common.TimeProvider;
import com.digitalwill.state.model.WillStateEntity;
import com.digitalwill.state.repository.WillStateRepository;
import com.digitalwill.verification.service.VerificationTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration AUTH_TOKEN_TTL = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final UserAuthTokenRepository tokenRepository;
    private final WillStateRepository willStateRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService tokenService;
    private final AuditLogService auditLogService;
    private final TimeProvider timeProvider;

    public AuthService(UserRepository userRepository,
                       UserAuthTokenRepository tokenRepository,
                       WillStateRepository willStateRepository,
                       PasswordEncoder passwordEncoder,
                       VerificationTokenService tokenService,
                       AuditLogService auditLogService,
                       TimeProvider timeProvider) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.willStateRepository = Objects.requireNonNull(willStateRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.tokenService = Objects.requireNonNull(tokenService);
        this.auditLogService = Objects.requireNonNull(auditLogService);
        this.timeProvider = Objects.requireNonNull(timeProvider);
    }

    public record AuthResponse(
            String token,
            UUID userId,
            String email,
            String fullName,
            UUID willId
    ) {}

    public record UserProfile(
            UUID id,
            String email,
            String fullName,
            Instant createdAt,
            UUID willId
    ) {}

    @Transactional
    public AuthResponse register(String email, String password, String fullName) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Instant now = timeProvider.now();
        UUID userId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(password);

        User user = new User(userId, normalizedEmail, passwordHash, fullName.trim(), now);
        userRepository.save(user);

        // Generate session token
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);
        UserAuthToken authToken = new UserAuthToken(UUID.randomUUID(), userId, tokenHash, now, now.plus(AUTH_TOKEN_TTL));
        tokenRepository.save(authToken);

        auditLogService.logCritical(
                null,
                userId.toString(),
                "USER",
                AuditAction.SECURITY_ALERT,
                AuditStatus.SUCCESS,
                AuditResourceType.WILL,
                userId.toString(),
                "{\"event\":\"USER_REGISTERED\",\"email\":\"" + normalizedEmail + "\"}"
        );

        log.info("User registered successfully: [{}] ({})", userId, normalizedEmail);
        return new AuthResponse(rawToken, userId, normalizedEmail, user.getFullName(), null);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email and password must not be blank");
        }

        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new SecurityException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new SecurityException("Invalid email or password");
        }

        Instant now = timeProvider.now();
        String rawToken = tokenService.generateRawToken();
        String tokenHash = tokenService.hashToken(rawToken);
        UserAuthToken authToken = new UserAuthToken(UUID.randomUUID(), user.getId(), tokenHash, now, now.plus(AUTH_TOKEN_TTL));
        tokenRepository.save(authToken);

        // Check if user already owns a Will
        Optional<WillStateEntity> willOpt = willStateRepository.findByOwnerId(user.getId()).stream().findFirst();
        UUID willId = willOpt.map(WillStateEntity::getId).orElse(null);

        // If user owns a Will, record verified owner activity on login (Section 14)
        if (willId != null) {
            try {
                // Update activity on successful authenticated login
                willOpt.ifPresent(w -> {
                    w.setLastVerifiedActivityAt(now);
                    w.setUpdatedAt(now);
                    willStateRepository.save(w);
                });
            } catch (Exception e) {
                log.warn("Failed to update activity on login for will {}: {}", willId, e.getMessage());
            }
        }

        auditLogService.logCritical(
                willId,
                user.getId().toString(),
                "USER",
                AuditAction.OWNER_ACTIVITY,
                AuditStatus.SUCCESS,
                AuditResourceType.WILL,
                user.getId().toString(),
                "{\"event\":\"USER_LOGIN\"}"
        );

        log.info("User logged in successfully: [{}]", user.getId());
        return new AuthResponse(rawToken, user.getId(), user.getEmail(), user.getFullName(), willId);
    }

    @Transactional
    public Optional<UserPrincipal> authenticateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = tokenService.hashToken(rawToken.trim());
        Optional<UserAuthToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        UserAuthToken token = tokenOpt.get();
        Instant now = timeProvider.now();
        if (now.isAfter(token.getExpiresAt())) {
            tokenRepository.delete(token);
            return Optional.empty();
        }

        token.setLastUsedAt(now);
        tokenRepository.save(token);

        return userRepository.findById(token.getUserId())
                .map(u -> new UserPrincipal(u.getId(), u.getEmail(), u.getFullName()));
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            String tokenHash = tokenService.hashToken(rawToken.trim());
            tokenRepository.deleteByTokenHash(tokenHash);
        }
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        UUID willId = willStateRepository.findByOwnerId(userId).stream()
                .findFirst()
                .map(WillStateEntity::getId)
                .orElse(null);
        return new UserProfile(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt(), willId);
    }
}
