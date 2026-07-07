package com.budget.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private static EncryptionService unlocked() {
        final EncryptionService service = new EncryptionService("");
        service.unlock(KeyWrapper.generateDek());
        return service;
    }

    @Test
    void encrypt_whileLocked_refusesToStoreSecrets() {
        final EncryptionService service = new EncryptionService("");

        assertThatThrownBy(() -> service.encrypt("access-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void encrypt_nullSecret_passesThroughEvenWhileLocked() {
        // Entities with no secret set (e.g. an OAuth connection with no API key column) must still save.
        final EncryptionService service = new EncryptionService("");

        assertThat(service.encrypt(null)).isNull();
        assertThat(service.decrypt(null)).isNull();
    }

    @Test
    void encrypt_whileUnlocked_roundTripsUnderTheV2Scheme() {
        final EncryptionService service = unlocked();

        final String encrypted = service.encrypt("access-token-12345");

        assertThat(encrypted).startsWith("enc:v2:").isNotEqualTo("access-token-12345");
        assertThat(service.decrypt(encrypted)).isEqualTo("access-token-12345");
    }

    @Test
    void encrypt_sameInputTwice_producesDistinctCiphertext() {
        // Random IV per call: no deterministic leakage of repeated secrets.
        final EncryptionService service = unlocked();

        assertThat(service.encrypt("same")).isNotEqualTo(service.encrypt("same"));
    }

    @Test
    void decrypt_afterLock_refusesUntilTheNextLogin() {
        final EncryptionService service = unlocked();
        final String encrypted = service.encrypt("secret");

        service.lock();

        assertThatThrownBy(() -> service.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void decrypt_withADifferentSessionKey_fails() {
        final EncryptionService service = unlocked();
        final String encrypted = service.encrypt("secret");

        service.unlock(KeyWrapper.generateDek());

        assertThatThrownBy(() -> service.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wrong key");
    }

    @Test
    void decrypt_legacyPlaintextRow_passesThrough() {
        assertThat(unlocked().decrypt("legacy-plaintext-token")).isEqualTo("legacy-plaintext-token");
    }

    @Test
    void decrypt_legacyV1Row_usesTheEnvironmentKey() throws Exception {
        final EncryptionService service = new EncryptionService("the-old-env-key");
        service.init();
        service.unlock(KeyWrapper.generateDek());

        assertThat(service.decrypt(legacyV1Ciphertext("the-old-env-key", "old-token")))
                .isEqualTo("old-token");
    }

    @Test
    void decrypt_legacyV1RowWithoutTheEnvironmentKey_explainsTheFix() {
        final EncryptionService service = unlocked();

        assertThatThrownBy(() -> service.decrypt("enc:v1:AAAA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");
    }

    /** Reproduces the retired v1 write path: AES-GCM under SHA-256(env key), 12-byte IV prepended. */
    private static String legacyV1Ciphertext(final String envKey, final String plaintext) throws Exception {
        final byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(envKey.getBytes(StandardCharsets.UTF_8));
        final byte[] iv = new byte[12];
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
        final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        final byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return "enc:v1:" + Base64.getEncoder().encodeToString(combined);
    }
}
