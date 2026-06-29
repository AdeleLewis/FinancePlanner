package com.budget.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptionServiceTest {

    private static EncryptionService withKey(String key) {
        EncryptionService service = new EncryptionService(key);
        service.init();
        return service;
    }

    @Test
    void roundTripsAValue() {
        EncryptionService service = withKey("a-long-random-test-passphrase");
        String secret = "access-token-12345";

        String encrypted = service.encrypt(secret);

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals(secret, encrypted);
        assertEquals(secret, service.decrypt(encrypted));
    }

    @Test
    void producesDistinctCiphertextEachTime() {
        // Random IV per call: same input must not yield the same ciphertext (no deterministic leakage).
        EncryptionService service = withKey("a-long-random-test-passphrase");
        assertNotEquals(service.encrypt("same"), service.encrypt("same"));
    }

    @Test
    void passesThroughLegacyPlaintextOnDecrypt() {
        EncryptionService service = withKey("a-long-random-test-passphrase");
        // A row written before encryption was enabled has no prefix and must be returned as-is.
        assertEquals("legacy-plaintext-token", service.decrypt("legacy-plaintext-token"));
    }

    @Test
    void handlesNulls() {
        EncryptionService service = withKey("a-long-random-test-passphrase");
        assertNull(service.encrypt(null));
        assertNull(service.decrypt(null));
    }

    @Test
    void wrongKeyFailsToDecrypt() {
        String encrypted = withKey("the-correct-key").encrypt("secret");
        assertThrows(IllegalStateException.class, () -> withKey("a-different-key").decrypt(encrypted));
    }

    @Test
    void noKeyMeansPassthrough() {
        EncryptionService service = withKey("");
        assertFalse(service.isEnabled());
        assertEquals("secret", service.encrypt("secret"));   // stored as-is, with a startup warning
    }
}
