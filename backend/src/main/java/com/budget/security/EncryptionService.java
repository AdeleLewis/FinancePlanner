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
 * Symmetric encryption for secret columns at rest (bank tokens / API keys).
 *
 * <p>Uses AES-256-GCM — authenticated encryption, so tampering is detected on decrypt — with a fresh
 * random 96-bit IV per value. The 256-bit key is derived (SHA-256) from {@code app.security.encryption-key}
 * (env {@code APP_ENCRYPTION_KEY}); it lives only in the process environment, never in the database, so a
 * stolen DB file is useless without it.
 *
 * <p>This protects against DB-file/backup theft and the H2 console, <em>not</em> against full host
 * compromise (an attacker with the running process also has the key). Stepping that up means custody in a
 * KMS/HSM — deliberately out of scope for a single-user, self-hosted app. The {@link #encrypt}/{@link #decrypt}
 * seam is the only thing a future KMS swap would touch.
 *
 * <p>Ciphertext is tagged with {@value #PREFIX} so {@link #decrypt} can pass through legacy plaintext rows
 * (written before encryption was enabled); the next write re-stores them encrypted.
 */
@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    /** Marks a value as produced by this service, and versions the scheme for future migration. */
    static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;          // 96-bit IV, the GCM-recommended size
    private static final int TAG_LENGTH_BITS = 128;

    private final String configuredKey;
    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec key;

    public EncryptionService(@Value("${app.security.encryption-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @PostConstruct
    void init() {
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("app.security.encryption-key (APP_ENCRYPTION_KEY) is not set — bank tokens/keys will be "
                    + "stored in PLAINTEXT. Set a long random value before exposing this app beyond localhost.");
            return;
        }
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialise encryption key", e);
        }
    }

    /** Whether encryption is active (a key was configured). When false, values are stored as-is. */
    public boolean isEnabled() {
        return key != null;
    }

    /** Encrypt a value for storage. Returns the input unchanged when no key is configured or input is null. */
    public String encrypt(String plaintext) {
        if (plaintext == null || key == null) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** Decrypt a value from storage. Legacy plaintext (no {@value #PREFIX}) and nulls pass through unchanged. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;   // null, or a legacy plaintext row written before encryption was enabled
        }
        if (key == null) {
            throw new IllegalStateException("Found encrypted data but no encryption key is configured. "
                    + "Set APP_ENCRYPTION_KEY to the value used when the data was written.");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed — wrong key, or the data was tampered with", e);
        }
    }
}
