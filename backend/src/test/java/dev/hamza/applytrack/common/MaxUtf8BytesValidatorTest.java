package dev.hamza.applytrack.common;

import dev.hamza.applytrack.common.validation.MaxUtf8Bytes;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaxUtf8BytesValidatorTest {

    record Holder(@MaxUtf8Bytes(8) String value) {
    }

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static boolean valid(String value) {
        return validator.validate(new Holder(value)).isEmpty();
    }

    @Test
    void countsUtf8BytesNotCharacters() {
        String eAcute = "é";                 // 2 bytes in UTF-8
        String emoji = "😀";            // 4 bytes in UTF-8

        assertThat(valid(null)).isTrue();
        assertThat(valid("abcdefgh")).isTrue();   // 8 bytes
        assertThat(valid("abcdefghi")).isFalse(); // 9 bytes
        assertThat(valid(eAcute.repeat(4))).isTrue();          // 4 chars, 8 bytes
        assertThat(valid(eAcute.repeat(4) + "a")).isFalse();   // 5 chars, 9 bytes
        assertThat(valid(emoji + emoji)).isTrue();              // 4 chars (2 code points), 8 bytes
        assertThat(valid(emoji + emoji + "a")).isFalse();
    }
}
