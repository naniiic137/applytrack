package dev.hamza.applytrack.auth;

import dev.hamza.applytrack.auth.AuthDtos.LoginRequest;
import dev.hamza.applytrack.auth.AuthDtos.RegisterRequest;
import dev.hamza.applytrack.common.ConflictException;
import dev.hamza.applytrack.user.User;
import dev.hamza.applytrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the parts of AuthService that are hard to trigger through the API (races, timing).
 */
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-04T09:00:00Z");
    private static final String DUMMY_HASH = "$2a$10$dummyhashdummyhashdummyhashdummyhashdummyhashdumm";

    private UserRepository users;
    private PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn(DUMMY_HASH);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtService jwt = new JwtService(new JwtProperties(
                "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWFwcGx5dHJhY2stdGVzdHMtMTIzNDU2Nzg=", Duration.ofHours(1)), clock);
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                new LoginRateLimitProperties(5, 20, Duration.ofMinutes(15)), clock);
        service = new AuthService(users, encoder, jwt, limiter, clock);
    }

    @Test
    void unknownEmailStillPaysForOneBcryptComparison() {
        when(users.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        when(encoder.matches(anyString(), anyString())).thenReturn(true); // even a "match" must not log in

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "guess-123"), "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);

        // same cost as a wrong password for a real account, so timing does not reveal registered emails
        verify(encoder, times(1)).matches(eq("guess-123"), eq(DUMMY_HASH));
    }

    @Test
    void registrationThatLosesARaceOnTheUniqueEmailIsA409NotA500() {
        when(users.existsByEmailIgnoreCase("race@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("Race@example.com", "secret-pass-1", "Racer")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An account with this email already exists");
    }
}
