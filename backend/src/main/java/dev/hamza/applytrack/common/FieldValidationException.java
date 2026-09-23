package dev.hamza.applytrack.common;

import java.util.Map;

/**
 * A validation error found by the service layer (after defaults are applied), reported in the same
 * "Validation failed" + field map shape as bean-validation errors so the UI can show it next to the field.
 */
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public FieldValidationException(String field, String message) {
        super(message);
        this.errors = Map.of(field, message);
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
