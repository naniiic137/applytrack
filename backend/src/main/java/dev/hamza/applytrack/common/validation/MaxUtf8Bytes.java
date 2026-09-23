package dev.hamza.applytrack.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be at most {@link #value()} bytes long once encoded as UTF-8.
 * {@code null} is valid (combine with {@code @NotBlank} when the value is required).
 *
 * <p>Needed for passwords: BCrypt only uses the first 72 <em>bytes</em>, and Spring Security refuses to
 * hash anything longer. A 72-character limit is not enough, because "é" is 2 bytes and an emoji is 4.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "is too long ({value} bytes maximum in UTF-8)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
