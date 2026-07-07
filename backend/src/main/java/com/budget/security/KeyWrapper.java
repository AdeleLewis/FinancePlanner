package com.budget.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Envelope-encryption primitives for password-based key custody.
 *
 * <p>The data-encryption key (DEK) that protects bank secrets is random — it has no relationship to the
 * password. What the password does is <em>wrap</em> it: a key-encryption key (KEK) is derived from the
 * password with PBKDF2-HMAC-SHA256 ({@value #PBKDF2_ITERATIONS} iterations, per-install random salt), and
 * the DEK is stored AES-GCM-encrypted under that KEK. Only the wrapped blob and the salt ever touch disk;
 * the DEK exists in plaintext solely in process memory after a successful login.
 *
 * <p>Consequences worth stating: without the password the wrapped blob is useless (GCM authentication
 * fails), and a forgotten password makes the DEK — and everything encrypted under it — unrecoverable.
 */
public final class KeyWrapper {

    /** OWASP-recommended work factor for PBKDF2-HMAC-SHA256; ~a few hundred ms per derivation. */
    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int DEK_LENGTH_BYTES = 32;    // AES-256
    private static final int KEK_LENGTH_BITS = 256;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private KeyWrapper() {
    }

    public static byte[] generateDek() {
        return randomBytes(DEK_LENGTH_BYTES);
    }

    public static byte[] generateSalt() {
        return randomBytes(SALT_LENGTH_BYTES);
    }

    public static byte[] deriveKek(final String password, final byte[] salt) {
        try {
            final PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS,
                    KEK_LENGTH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive a key from the password", e);
        }
    }

    /** Encrypts the DEK under the KEK; the result (base64 of IV + ciphertext) is safe to persist. */
    public static String wrap(final byte[] dek, final byte[] kek) {
        try {
            final byte[] iv = randomBytes(IV_LENGTH);
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kek, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            final byte[] ciphertext = cipher.doFinal(dek);

            final byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Could not wrap the data key", e);
        }
    }

    /** Recovers the DEK. Throws when the KEK is wrong (i.e. the password didn't produce the same key). */
    public static byte[] unwrap(final String wrapped, final byte[] kek) {
        try {
            final byte[] combined = Base64.getDecoder().decode(wrapped);
            final byte[] iv = new byte[IV_LENGTH];
            final byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kek, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Could not unwrap the data key — wrong password-derived key, "
                    + "or the stored blob was tampered with", e);
        }
    }

    private static byte[] randomBytes(final int length) {
        final byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
