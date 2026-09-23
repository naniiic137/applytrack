package dev.hamza.applytrack.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies HS256-signed access tokens.
 *
 * <p>The signing key comes from {@code app.jwt.secret} (env {@code JWT_SECRET}): a Base64-encoded random value
 * of at least 256 bits. Generate one with {@code openssl rand -base64 48}. The app refuses to start with a
 * missing, non-Base64 or too-short secret, so a misconfiguration shows up at deploy time, not at first login.
 */
@Service
public class JwtService {

    private static final String ISSUER = "applytrack";
    private static final String EMAIL_CLAIM = "email";
    static final int MIN_KEY_BYTES = 32; // HS256 needs at least 256 bits
    private static final String HOW_TO_GENERATE = "Generate one with: openssl rand -base64 48";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = signingKey(properties.secret());
        this.properties = properties;
        this.clock = clock;
    }

    static SecretKey signingKey(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) must be set to a Base64-encoded key of at least 256 bits. "
                            + HOW_TO_GENERATE);
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret.trim());
        } catch (DecodingException | IllegalArgumentException e) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) is not valid Base64. " + HOW_TO_GENERATE);
        }
        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) decodes to " + bytes.length
                    + " bytes; at least " + MIN_KEY_BYTES + " bytes (256 bits) are required. " + HOW_TO_GENERATE);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String issueToken(Long userId, String email) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim(EMAIL_CLAIM, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt(now)))
                .signWith(key)
                .compact();
    }

    public Instant expiresAt(Instant issuedAt) {
        return issuedAt.plus(properties.expiration());
    }

    /**
     * @return the principal if the token is well-formed, signed with our key and not expired; empty otherwise
     */
    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthUser(Long.valueOf(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
