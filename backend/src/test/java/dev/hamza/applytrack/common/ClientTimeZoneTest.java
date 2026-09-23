package dev.hamza.applytrack.common;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTimeZoneTest {

    @Test
    void acceptsIanaIdsAndFallsBackToUtc() {
        assertThat(ClientTimeZone.resolve("Africa/Tunis")).isEqualTo(ZoneId.of("Africa/Tunis"));
        assertThat(ClientTimeZone.resolve(" Europe/Paris ")).isEqualTo(ZoneId.of("Europe/Paris"));
        assertThat(ClientTimeZone.resolve(null)).isEqualTo(ZoneOffset.UTC);
        assertThat(ClientTimeZone.resolve("")).isEqualTo(ZoneOffset.UTC);
        assertThat(ClientTimeZone.resolve("Mars/Olympus_Mons")).isEqualTo(ZoneOffset.UTC);
        assertThat(ClientTimeZone.resolve("x".repeat(500))).isEqualTo(ZoneOffset.UTC);
    }
}
