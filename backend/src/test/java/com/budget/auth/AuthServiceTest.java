package com.budget.auth;

import com.budget.auth.AuthService.LoginResult;
import com.budget.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct horse battery";

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final EncryptionService encryptionService = new EncryptionService("");

    @Mock
    private UserCredentialRepository repository;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(repository, encoder, encryptionService);
        lenient().when(repository.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(new UserCredential(encoder.encode(PASSWORD))));
    }

    @Test
    void verify_correctPassword_succeedsAndUnlocksTheVault() {
        assertThat(service.verify(PASSWORD)).isEqualTo(LoginResult.SUCCESS);
        assertThat(encryptionService.isUnlocked()).isTrue();
    }

    @Test
    void verify_wrongPassword_isRejectedAndStaysLocked() {
        assertThat(service.verify("not the password")).isEqualTo(LoginResult.WRONG_PASSWORD);
        assertThat(encryptionService.isUnlocked()).isFalse();
    }

    @Test
    void setup_thenLoginAfterRestart_recoversTheSameEncryptionKey() {
        // Setup: a data key is generated, wrapped under the password, and only the wrapped blob saved.
        service.setup(PASSWORD);
        final ArgumentCaptor<UserCredential> saved = ArgumentCaptor.forClass(UserCredential.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().hasVault()).isTrue();
        final String ciphertext = encryptionService.encrypt("bank-refresh-token");

        // Restart/logout: the in-memory key is gone; the ciphertext is unreadable.
        encryptionService.lock();
        lenient().when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(saved.getValue()));

        // Login unwraps the same data key from the stored blob — the old ciphertext decrypts again.
        assertThat(service.verify(PASSWORD)).isEqualTo(LoginResult.SUCCESS);
        assertThat(encryptionService.decrypt(ciphertext)).isEqualTo("bank-refresh-token");
    }

    @Test
    void verify_credentialFromBeforeTheVaultExisted_getsOneOnFirstLogin() {
        // The stubbed credential has no vault columns (pre-migration row).
        assertThat(service.verify(PASSWORD)).isEqualTo(LoginResult.SUCCESS);

        final ArgumentCaptor<UserCredential> saved = ArgumentCaptor.forClass(UserCredential.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().hasVault()).isTrue();
        assertThat(encryptionService.isUnlocked()).isTrue();
    }

    @Test
    void verify_fiveConsecutiveFailures_locksEvenTheRightPassword() {
        for (int i = 0; i < 5; i++) {
            assertThat(service.verify("wrong")).isEqualTo(LoginResult.WRONG_PASSWORD);
        }

        assertThat(service.verify(PASSWORD)).isEqualTo(LoginResult.LOCKED);
        assertThat(service.lockSecondsRemaining()).isPositive();
    }

    @Test
    void verify_successResetsTheFailureCount() {
        for (int i = 0; i < 4; i++) {
            service.verify("wrong");
        }
        assertThat(service.verify(PASSWORD)).isEqualTo(LoginResult.SUCCESS);

        // The counter restarted, so four more failures still don't lock.
        for (int i = 0; i < 4; i++) {
            assertThat(service.verify("wrong")).isEqualTo(LoginResult.WRONG_PASSWORD);
        }
        assertThat(service.verify(PASSWORD)).isEqualTo(LoginResult.SUCCESS);
    }

    @Test
    void lock_sealsTheVault() {
        service.verify(PASSWORD);
        assertThat(encryptionService.isUnlocked()).isTrue();

        service.lock();

        assertThat(encryptionService.isUnlocked()).isFalse();
    }

    @Test
    void validatePassword_tooShort_isRejected() {
        assertThatThrownBy(() -> AuthService.validatePassword("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 8");
    }

    @Test
    void validatePassword_beyondBcryptLimit_isRejected() {
        assertThatThrownBy(() -> AuthService.validatePassword("x".repeat(73)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72");
    }
}
