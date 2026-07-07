package com.budget.auth;

import com.budget.security.EncryptionService;
import com.budget.security.KeyWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Single-user password authentication, doubling as custody for the encryption key.
 *
 * <p>Storage: only a salted, one-way hash (BCrypt via the delegating encoder, {@code {bcrypt}...}) is
 * persisted. Verification re-hashes the presented password and compares — the real password is never
 * written anywhere, so a stolen database file cannot be reversed into it.
 *
 * <p>Key custody: setup generates a random data key, wraps it under a key derived from the password
 * ({@link KeyWrapper}), and stores only the wrapped blob. Every successful login unwraps it and hands it
 * to {@link EncryptionService}; logout drops it. One password opens both the door and the vault, and
 * nothing on disk can decrypt the bank secrets without it — including on a stolen laptop.
 *
 * <p>Brute force: after {@value #MAX_FAILURES_BEFORE_LOCK} consecutive wrong attempts, logins lock for
 * {@code LOCK_DURATION}. Combined with BCrypt's deliberate slowness plus the PBKDF2 unwrap on success,
 * online guessing is capped at a few attempts per minute.
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
    private final EncryptionService encryptionService;

    private int consecutiveFailures;
    private Instant lockedUntil = Instant.EPOCH;

    public AuthService(UserCredentialRepository repository, PasswordEncoder passwordEncoder,
                       EncryptionService encryptionService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.encryptionService = encryptionService;
    }

    public boolean isSetup() {
        return repository.count() > 0;
    }

    /** Creates the owner credential and vault. Callers must reject this when {@link #isSetup()} is true. */
    public void setup(final String rawPassword) {
        validatePassword(rawPassword);
        final UserCredential credential = new UserCredential(passwordEncoder.encode(rawPassword));
        final byte[] dek = wrapNewDekInto(credential, rawPassword);
        repository.save(credential);
        encryptionService.unlock(dek);
    }

    @Transactional
    public synchronized LoginResult verify(final String rawPassword) {
        if (Instant.now().isBefore(lockedUntil)) {
            return LoginResult.LOCKED;
        }
        final UserCredential credential = repository.findFirstByOrderByIdAsc().orElse(null);
        final boolean matches = credential != null
                && passwordEncoder.matches(rawPassword == null ? "" : rawPassword,
                        credential.getPasswordHash());
        if (!matches) {
            consecutiveFailures++;
            if (consecutiveFailures >= MAX_FAILURES_BEFORE_LOCK) {
                lockedUntil = Instant.now().plus(LOCK_DURATION);
                consecutiveFailures = 0;
            }
            return LoginResult.WRONG_PASSWORD;
        }
        consecutiveFailures = 0;
        encryptionService.unlock(recoverDek(credential, rawPassword));
        return LoginResult.SUCCESS;
    }

    /** Drops the in-memory encryption key; encrypted bank secrets stay sealed until the next login. */
    public void lock() {
        encryptionService.lock();
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
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    /**
     * Unwraps the credential's data key with the (already verified) password. Credentials created before
     * the vault existed get one now — this is the only moment the password is available to wrap with.
     */
    private byte[] recoverDek(final UserCredential credential, final String rawPassword) {
        if (!credential.hasVault()) {
            final byte[] dek = wrapNewDekInto(credential, rawPassword);
            repository.save(credential);
            return dek;
        }
        final byte[] salt = Base64.getDecoder().decode(credential.getKekSalt());
        return KeyWrapper.unwrap(credential.getWrappedDek(), KeyWrapper.deriveKek(rawPassword, salt));
    }

    private static byte[] wrapNewDekInto(final UserCredential credential, final String rawPassword) {
        final byte[] dek = KeyWrapper.generateDek();
        final byte[] salt = KeyWrapper.generateSalt();
        final String wrapped = KeyWrapper.wrap(dek, KeyWrapper.deriveKek(rawPassword, salt));
        credential.attachVault(Base64.getEncoder().encodeToString(salt), wrapped);
        return dek;
    }
}
