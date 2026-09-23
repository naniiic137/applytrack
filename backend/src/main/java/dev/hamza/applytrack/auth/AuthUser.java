package dev.hamza.applytrack.auth;

/**
 * The authenticated principal, rebuilt from the JWT on every request (no session, no DB lookup).
 */
public record AuthUser(Long id, String email) {
}
