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

@Service
public class AuthService {

    private static final String DUPLICATE_EMAIL = "An account with this email already exists";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return tokenFor(user);
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
