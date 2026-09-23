package dev.hamza.applytrack.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Failed-login limits, see {@link LoginAttemptLimiter}.
 *
 * @param maxFailuresPerEmail failed attempts allowed per account inside the window
 * @param maxFailuresPerIp    failed attempts allowed per client IP inside the window (any account)
 * @param window              length of the sliding window
 */
@ConfigurationProperties(prefix = "app.auth.login-rate-limit")
public record LoginRateLimitProperties(int maxFailuresPerEmail, int maxFailuresPerIp, Duration window) {

    public LoginRateLimitProperties {
        if (maxFailuresPerEmail <= 0) {
            maxFailuresPerEmail = 5;
        }
        if (maxFailuresPerIp <= 0) {
            maxFailuresPerIp = 20;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(15);
        }
    }
}
