package dev.hamza.applytrack.stats;

import dev.hamza.applytrack.application.ApplicationStatus;
import dev.hamza.applytrack.application.InterviewType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dashboard numbers for the current user.
 *
 * @param submitted     applications whose current status is not WISHLIST (the denominator of both rates)
 * @param responseRate  % of submitted applications that reached INTERVIEW, OFFER or REJECTED
 * @param interviewRate % of submitted applications that reached INTERVIEW or OFFER
 *                      (for both rates, a move undone within 24 hours does not count; see StatsService)
 */
public record StatsResponse(
        long total,
        long active,
        long submitted,
        Map<ApplicationStatus, Long> byStatus,
        double responseRate,
        double interviewRate,
        List<WeekCount> applicationsPerWeek,
        List<FollowUp> upcomingFollowUps,
        List<UpcomingInterview> upcomingInterviews) {

    public record WeekCount(LocalDate weekStart, long count) {
    }

    public record FollowUp(Long applicationId, String company, String role, ApplicationStatus status,
                           LocalDate followUpOn, boolean overdue) {
    }

    public record UpcomingInterview(Long interviewId, Long applicationId, String company, String role,
                                    Instant scheduledAt, InterviewType type) {
    }
}
