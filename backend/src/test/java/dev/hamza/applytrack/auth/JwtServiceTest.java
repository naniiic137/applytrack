package dev.hamza.applytrack.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String KEY_A = "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWFwcGx5dHJhY2stdGVzdHMtMTIzNDU2Nzg=";
    private static final String KEY_B = "YW5vdGhlci10ZXN0LXNlY3JldC1rZXktdGhhdC1pcy1sb25nLWVub3VnaC0xMjM0NTY=";
    private static final Instant NOW = Instant.parse("2026-03-02T10:00:00Z");

    private static JwtService service(String key, Instant now) {
        return new JwtService(new JwtProperties(key, Duration.ofHours(1)), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenRoundTrips() {
        JwtService jwt = service(KEY_A, NOW);

        assertThat(jwt.parse(jwt.issueToken(42L, "a@example.com")))
                .contains(new AuthUser(42L, "a@example.com"));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(KEY_A, NOW).issueToken(1L, "a@example.com");

        assertThat(service(KEY_A, NOW.plus(Duration.ofMinutes(61))).parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service(KEY_B, NOW).issueToken(1L, "a@example.com");

        assertThat(service(KEY_A, NOW).parse(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(service(KEY_A, NOW).parse("not.a.jwt")).isEmpty();
        assertThat(service(KEY_A, NOW).parse("")).isEmpty();
    }

    @Test
    void missingSecretFailsFast() {
        assertThatThrownBy(() -> service("", NOW)).isInstanceOf(IllegalStateException.class);
    }
}
