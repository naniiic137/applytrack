package dev.hamza.applytrack.auth;

import dev.hamza.applytrack.common.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limit on <em>failed</em> logins, per account and per client IP.
 *
 * <p>Per account, it slows down password guessing against one user; per IP, it slows down one client trying
 * many accounts. Only failures count, so a user who types the password right is never blocked, and a
 * successful login clears that account's failures. Blocked attempts are not checked against the password
 * at all (and not counted), and the caller learns when to retry through {@link TooManyRequestsException}.
 *
 * <p>State lives in this JVM only: fine for a single instance. Several instances would need a shared store
 * (Redis, or a rate limit at the reverse proxy).
 */
@Component
public class LoginAttemptLimiter {

    /** Above this many tracked keys, stale entries are swept so the map cannot grow without bound. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final LoginRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @throws TooManyRequestsException if this IP or this account has used up its failed attempts
     */
    public void checkAllowed(String clientIp, String email) {
        Instant now = clock.instant();
        Duration ipWait = retryAfter(ipKey(clientIp), properties.maxFailuresPerIp(), now);
        Duration emailWait = retryAfter(emailKey(email), properties.maxFailuresPerEmail(), now);
        Duration wait = ipWait.compareTo(emailWait) >= 0 ? ipWait : emailWait;
        if (!wait.isZero()) {
            throw new TooManyRequestsException("Too many failed login attempts. Try again later.", wait);
        }
    }

    public void recordFailure(String clientIp, String email) {
        Instant now = clock.instant();
        record(ipKey(clientIp), now);
        record(emailKey(email), now);
        if (failures.size() > SWEEP_THRESHOLD) {
            sweep(now);
        }
    }

    public void recordSuccess(String email) {
        failures.remove(emailKey(email));
    }

    private void record(String key, Instant now) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts, now);
            attempts.addLast(now);
        }
    }

    private Duration retryAfter(String key, int max, Instant now) {
        Deque<Instant> attempts = failures.get(key);
        if (attempts == null) {
            return Duration.ZERO;
        }
        synchronized (attempts) {
            prune(attempts, now);
            if (attempts.size() < max) {
                return Duration.ZERO;
            }
            // allowed again once enough of the oldest failures have left the window
            Instant unblockedAt = attempts.stream().skip(attempts.size() - max).findFirst().orElseThrow()
                    .plus(properties.window());
            return Duration.between(now, unblockedAt);
        }
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(properties.window());
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    private void sweep(Instant now) {
        failures.entrySet().removeIf(entry -> {
            Deque<Instant> attempts = entry.getValue();
            synchronized (attempts) {
                prune(attempts, now);
                return attempts.isEmpty();
            }
        });
    }

    private static String ipKey(String clientIp) {
        return "ip:" + (clientIp == null ? "unknown" : clientIp);
    }

    private static String emailKey(String email) {
        return "email:" + email;
    }
}
