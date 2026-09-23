package dev.hamza.applytrack.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
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
 */
@Service
public class JwtService {

    private static final String ISSUER = "applytrack";
    private static final String EMAIL_CLAIM = "email";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        if (!StringUtils.hasText(properties.secret())) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) must be set to a Base64-encoded key of at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.properties = properties;
        this.clock = clock;
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
