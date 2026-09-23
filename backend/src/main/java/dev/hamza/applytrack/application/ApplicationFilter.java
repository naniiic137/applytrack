package dev.hamza.applytrack.application;

import java.util.List;

/**
 * Query-string filters for GET /api/applications.
 *
 * @param statuses zero or more statuses (repeat the parameter: ?status=APPLIED&status=INTERVIEW)
 * @param q        free-text search on company, role and location
 * @param tag      exact tag match (case-insensitive)
 */
public record ApplicationFilter(List<ApplicationStatus> statuses, String q, String tag) {
}
