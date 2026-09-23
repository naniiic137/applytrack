package dev.hamza.applytrack.auth;

import dev.hamza.applytrack.common.TooManyRequestsException;
import dev.hamza.applytrack.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class LoginAttemptLimiterTest {

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-03-04T09:00:00Z"));
        limiter = new LoginAttemptLimiter(new LoginRateLimitProperties(3, 5, Duration.ofMinutes(10)), clock);
    }

    private void fail(String ip, String email, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkAllowed(ip, email);
            limiter.recordFailure(ip, email);
            clock.advance(Duration.ofMinutes(1));
        }
    }

    @Test
    void blocksAnAccountAfterTooManyFailuresAndSaysWhenToRetry() {
        fail("1.1.1.1", "a@example.com", 3); // failures at 09:00, 09:01, 09:02; now 09:03

        TooManyRequestsException blocked = catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.checkAllowed("2.2.2.2", "a@example.com")); // another IP does not help

        assertThat(blocked).isNotNull();
        assertThat(blocked.getRetryAfter()).isEqualTo(Duration.ofMinutes(7)); // oldest failure leaves at 09:10
        assertThat(blocked.getRetryAfterSeconds()).isEqualTo(420);
        assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "b@example.com")).doesNotThrowAnyException();
    }

    @Test
    void theWindowSlides() {
        fail("1.1.1.1", "a@example.com", 3);

        clock.advance(Duration.ofMinutes(7)); // 09:10: the 09:00 failure has expired

        assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void blocksAnIpThatTriesManyAccounts() {
        for (int i = 0; i < 5; i++) {
            fail("6.6.6.6", "user" + i + "@example.com", 1);
        }

        assertThat(catchThrowableOfType(TooManyRequestsException.class,
                () -> limiter.checkAllowed("6.6.6.6", "fresh@example.com"))).isNotNull();
        assertThatCode(() -> limiter.checkAllowed("7.7.7.7", "fresh@example.com")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginClearsTheAccountsFailures() {
        fail("1.1.1.1", "a@example.com", 2);
        limiter.recordSuccess("a@example.com");
        fail("1.1.1.1", "a@example.com", 2);

        assertThatCode(() -> limiter.checkAllowed("1.1.1.1", "a@example.com")).doesNotThrowAnyException();
    }
}
