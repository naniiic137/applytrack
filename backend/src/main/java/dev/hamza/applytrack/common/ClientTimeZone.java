package dev.hamza.applytrack.common;

import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The user's time zone, sent by the frontend as an IANA id in the {@value #HEADER} header
 * (e.g. {@code Africa/Tunis}). It decides what "today" means: the default applied date, overdue
 * follow-ups and the weekly buckets. Missing or unknown values fall back to UTC.
 */
public final class ClientTimeZone {

    public static final String HEADER = "X-Time-Zone";

    private ClientTimeZone() {
    }

    public static ZoneId resolve(String header) {
        if (!StringUtils.hasText(header) || header.length() > 64) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(header.trim());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
