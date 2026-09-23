package dev.hamza.applytrack.auth;

import dev.hamza.applytrack.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** BCrypt only hashes the first 72 bytes, so that is the real limit (not 72 characters). */
    public static final int PASSWORD_MAX_BYTES = 72;

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank
            @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
            @MaxUtf8Bytes(value = PASSWORD_MAX_BYTES,
                    message = "must be at most 72 bytes (accented letters and emoji count as 2 to 4 bytes each)")
            String password,
            @NotBlank @Size(max = 100) String displayName) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserResponse(Long id, String email, String displayName) {
    }

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
    }
}
