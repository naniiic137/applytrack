package dev.hamza.applytrack.auth;

import dev.hamza.applytrack.auth.AuthDtos.AuthResponse;
import dev.hamza.applytrack.auth.AuthDtos.LoginRequest;
import dev.hamza.applytrack.auth.AuthDtos.RegisterRequest;
import dev.hamza.applytrack.auth.AuthDtos.UserResponse;
import dev.hamza.applytrack.common.ConflictException;
import dev.hamza.applytrack.common.NotFoundException;
import dev.hamza.applytrack.user.User;
import dev.hamza.applytrack.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final String DUPLICATE_EMAIL = "An account with this email already exists";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginLimiter;
    private final Clock clock;

    /** Hash of a random password, compared against when the email is unknown (see {@link #login}). */
    private volatile String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptLimiter loginLimiter, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginLimiter = loginLimiter;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(DUPLICATE_EMAIL);
        }
        User user;
        try {
            // flush now so a concurrent registration that won the race hits the unique constraint here
            user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password()),
                    request.displayName().trim(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICATE_EMAIL);
        }
        return tokenFor(user);
    }

    /**
     * Unknown email and wrong password give the same 401, and take the same time: when the account does not
     * exist, the password is still checked against a dummy BCrypt hash, so response timing does not reveal
     * which emails are registered.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = normalize(request.email());
        loginLimiter.checkAllowed(clientIp, email);

        Optional<User> user = users.findByEmailIgnoreCase(email);
        String hash = user.map(User::getPasswordHash).orElseGet(this::dummyHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            loginLimiter.recordFailure(clientIp, email);
            throw new BadCredentialsException("Invalid email or password");
        }
        loginLimiter.recordSuccess(email);
        return tokenFor(user.get());
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode(UUID.randomUUID().toString());
            dummyHash = hash;
        }
        return hash;
    }

    @Transactional(readOnly = true)
    public UserResponse me(AuthUser principal) {
        return users.findById(principal.id())
                .map(AuthService::toResponse)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private AuthResponse tokenFor(User user) {
        Instant now = clock.instant();
        return new AuthResponse(jwtService.issueToken(user.getId(), user.getEmail()), jwtService.expiresAt(now),
                toResponse(user));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
