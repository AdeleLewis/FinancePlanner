package com.budget.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Symmetric encryption for secret columns at rest (bank tokens / API keys), AES-256-GCM with a fresh
 * random 96-bit IV per value.
 *
 * <p><b>Key custody:</b> the data key is unwrapped from the owner's login password (see
 * {@link KeyWrapper}) and lives only in process memory between login and logout. Nothing on disk can
 * decrypt the {@code enc:v2:} rows without that password: a stolen database file (or whole laptop, absent
 * the password) yields only ciphertext. The cost of that guarantee: bank secrets are unreadable while the
 * app is locked, and an unrecoverable password means re-connecting the banks.
 *
 * <p><b>Formats:</b> {@code enc:v2:} rows use the password-unwrapped session key. Legacy {@code enc:v1:}
 * rows were keyed from the {@code APP_ENCRYPTION_KEY} environment variable — that variable is now read
 * only to <em>decrypt</em> old rows (they re-store as v2 on next write). Prefix-less values are legacy
 * plaintext and pass through on read.
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    /** Legacy scheme: key derived from the APP_ENCRYPTION_KEY environment variable. Decrypt-only. */
    static final String PREFIX_V1 = "enc:v1:";
    /** Current scheme: session key unwrapped from the login password. */
    static final String PREFIX_V2 = "enc:v2:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;          // 96-bit IV, the GCM-recommended size
    private static final int TAG_LENGTH_BITS = 128;

    private final String legacyConfiguredKey;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec legacyKey;
    private volatile SecretKeySpec sessionKey;

    public EncryptionService(@Value("${app.security.encryption-key:}") String legacyConfiguredKey) {
        this.legacyConfiguredKey = legacyConfiguredKey;
    }

    @PostConstruct
    void init() {
        if (legacyConfiguredKey == null || legacyConfiguredKey.isBlank()) {
            return;
        }
        try {
            final byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(legacyConfiguredKey.getBytes(StandardCharsets.UTF_8));
            legacyKey = new SecretKeySpec(derived, "AES");
            log.info("APP_ENCRYPTION_KEY is set — it is now used only to read legacy enc:v1 rows, which "
                    + "re-encrypt under the login password on their next write. Unset it once none remain.");
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialise the legacy encryption key", e);
        }
    }

    /** Installs the session key unwrapped from the login password. Called on every successful login. */
    public void unlock(final byte[] dataKey) {
        sessionKey = new SecretKeySpec(dataKey, "AES");
    }

    /** Drops the session key (logout / lock). Encrypted values become unreadable until the next login. */
    public void lock() {
        sessionKey = null;
    }

    public boolean isUnlocked() {
        return sessionKey != null;
    }

    /** Encrypt a value for storage under the session key. Null passes through (no secret to protect). */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        final SecretKeySpec key = sessionKey;
        if (key == null) {
            throw new IllegalStateException(
                    "The app is locked — log in before storing bank secrets");
        }
        try {
            final byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            final byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX_V2 + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** Decrypt a stored value. Legacy plaintext (no prefix) and nulls pass through unchanged. */
    public String decrypt(String stored) {
        if (stored == null) {
            return stored;
        }
        if (stored.startsWith(PREFIX_V2)) {
            final SecretKeySpec key = sessionKey;
            if (key == null) {
                throw new IllegalStateException(
                        "The app is locked — log in before reading bank secrets");
            }
            return decryptWith(key, stored.substring(PREFIX_V2.length()));
        }
        if (stored.startsWith(PREFIX_V1)) {
            if (legacyKey == null) {
                throw new IllegalStateException("Found data encrypted under the old APP_ENCRYPTION_KEY "
                        + "scheme but that variable is not set. Set it once more so the data can be read "
                        + "and migrated.");
            }
            return decryptWith(legacyKey, stored.substring(PREFIX_V1.length()));
        }
        return stored;   // legacy plaintext row written before encryption existed
    }

    private String decryptWith(final SecretKeySpec key, final String encoded) {
        try {
            final byte[] combined = Base64.getDecoder().decode(encoded);
            final byte[] iv = new byte[IV_LENGTH];
            final byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed — wrong key, or the data was tampered with", e);
        }
    }
}
