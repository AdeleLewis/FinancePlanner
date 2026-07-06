package com.budget.auth;

import com.budget.auth.AuthService.LoginResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "correct horse battery";

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private UserCredentialRepository repository;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(repository, encoder);
        lenient().when(repository.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(new UserCredential(encoder.encode(PASSWORD))));
    }

    @Test
    void verify_correctPassword_succeeds() {
        assertThat(service.verify(PASSWORD)).isEqualTo(LoginResult.SUCCESS);
    }

    @Test
    void verify_wrongPassword_isRejected() {
        assertThat(service.verify("not the password")).isEqualTo(LoginResult.WRONG_PASSWORD);
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
