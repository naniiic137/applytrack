package dev.hamza.applytrack.common;

/**
 * Thrown when a resource does not exist or does not belong to the current user.
 * Both cases map to 404 so that other users' ids are not revealed.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
