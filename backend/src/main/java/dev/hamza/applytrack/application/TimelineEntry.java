package dev.hamza.applytrack.application;

import java.time.Instant;

/** Lightweight projection of a {@link StatusChange} row, used by the stats queries. */
public record TimelineEntry(Long applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus,
                            Instant changedAt) {
}
