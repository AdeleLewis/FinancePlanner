package com.budget.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Single-user password authentication.
 *
 * <p>Storage: only a salted, one-way hash (BCrypt via the delegating encoder, {@code {bcrypt}...}) is
 * persisted. Verification re-hashes the presented password and compares — the real password is never
 * written anywhere, so a stolen database file cannot be reversed into it.
 *
 * <p>Brute force: after {@value #MAX_FAILURES_BEFORE_LOCK} consecutive wrong attempts, logins lock for
 * {@code LOCK_DURATION}. Combined with BCrypt's deliberate slowness (~100ms/attempt), online guessing is
 * capped at a few attempts per minute.
 */
@Service
public class AuthService {

    public enum LoginResult {
        SUCCESS,
        WRONG_PASSWORD,
        LOCKED
    }

    static final int MIN_PASSWORD_LENGTH = 8;
    /** BCrypt only reads the first 72 bytes; refuse longer input rather than silently truncating. */
    static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_FAILURES_BEFORE_LOCK = 5;
    private static final Duration LOCK_DURATION = Duration.ofSeconds(30);

    private final UserCredentialRepository repository;
    private final PasswordEncoder passwordEncoder;

    private int consecutiveFailures;
    private Instant lockedUntil = Instant.EPOCH;

    public AuthService(UserCredentialRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isSetup() {
        return repository.count() > 0;
    }

    /** Creates the owner credential. Callers must reject the request when {@link #isSetup()} is true. */
    public void setup(final String rawPassword) {
        validatePassword(rawPassword);
        repository.save(new UserCredential(passwordEncoder.encode(rawPassword)));
    }

    public synchronized LoginResult verify(final String rawPassword) {
        if (Instant.now().isBefore(lockedUntil)) {
            return LoginResult.LOCKED;
        }
        final boolean matches = repository.findFirstByOrderByIdAsc()
                .map(credential -> passwordEncoder.matches(rawPassword == null ? "" : rawPassword,
                        credential.getPasswordHash()))
                .orElse(false);
        if (!matches) {
            consecutiveFailures++;
            if (consecutiveFailures >= MAX_FAILURES_BEFORE_LOCK) {
                lockedUntil = Instant.now().plus(LOCK_DURATION);
                consecutiveFailures = 0;
            }
            return LoginResult.WRONG_PASSWORD;
        }
        consecutiveFailures = 0;
        return LoginResult.SUCCESS;
    }

    public synchronized long lockSecondsRemaining() {
        final long seconds = Duration.between(Instant.now(), lockedUntil).getSeconds();
        return Math.max(seconds, 0);
    }

    static void validatePassword(final String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
    }
}
