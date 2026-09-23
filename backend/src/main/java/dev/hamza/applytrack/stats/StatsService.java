package dev.hamza.applytrack.stats;

import dev.hamza.applytrack.application.ApplicationStatus;
import dev.hamza.applytrack.application.InterviewRepository;
import dev.hamza.applytrack.application.JobApplicationRepository;
import dev.hamza.applytrack.application.StatusCount;
import dev.hamza.applytrack.application.TimelineEntry;
import dev.hamza.applytrack.stats.StatsResponse.FollowUp;
import dev.hamza.applytrack.stats.StatsResponse.UpcomingInterview;
import dev.hamza.applytrack.stats.StatsResponse.WeekCount;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StatsService {

    static final int WEEKS = 12;
    static final int FOLLOW_UP_WINDOW_DAYS = 14;
    private static final int LIST_LIMIT = 8;

    /**
     * A move that is reverted (back to the status it came from) within this window is treated as a
     * mis-click or mis-drag and does not count as having reached that status.
     */
    static final Duration UNDO_WINDOW = Duration.ofHours(24);

    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;
    private final Clock clock;

    public StatsService(JobApplicationRepository applications, InterviewRepository interviews, Clock clock) {
        this.applications = applications;
        this.interviews = interviews;
        this.clock = clock;
    }

    public StatsResponse forUser(Long ownerId) {
        LocalDate today = LocalDate.now(clock);

        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            byStatus.put(s, 0L);
        }
        for (StatusCount c : applications.countByStatus(ownerId)) {
            byStatus.put(c.status(), c.count());
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long active = ApplicationStatus.ACTIVE.stream().mapToLong(byStatus::get).sum();
        long submitted = total - byStatus.get(ApplicationStatus.WISHLIST);

        // Rates are computed over submitted applications only (current status is not WISHLIST), so the
        // numerator is always a subset of the denominator and a rate can never exceed 100%.
        long responded = 0;
        long interviewed = 0;
        for (List<TimelineEntry> timeline : timelinesOfSubmitted(ownerId)) {
            Set<ApplicationStatus> reached = reachedStatuses(timeline);
            if (!Collections.disjoint(reached, ApplicationStatus.RESPONSES)) {
                responded++;
            }
            if (!Collections.disjoint(reached, ApplicationStatus.INTERVIEWED)) {
                interviewed++;
            }
        }

        List<FollowUp> followUps = applications
                .findFollowUpsDueBy(ownerId, today.plusDays(FOLLOW_UP_WINDOW_DAYS), ApplicationStatus.ACTIVE,
                        Limit.of(LIST_LIMIT))
                .stream()
                .map(a -> new FollowUp(a.getId(), a.getCompany(), a.getRole(), a.getStatus(), a.getFollowUpOn(),
                        a.getFollowUpOn().isBefore(today)))
                .toList();

        List<UpcomingInterview> upcoming = interviews
                .findUpcoming(ownerId, clock.instant(), Limit.of(LIST_LIMIT))
                .stream()
                .map(i -> new UpcomingInterview(i.getId(), i.getApplication().getId(),
                        i.getApplication().getCompany(), i.getApplication().getRole(), i.getScheduledAt(),
                        i.getType()))
                .toList();

        return new StatsResponse(total, active, submitted, new LinkedHashMap<>(byStatus),
                percentage(responded, submitted), percentage(interviewed, submitted),
                weeklyCounts(ownerId, today), followUps, upcoming);
    }

    private List<List<TimelineEntry>> timelinesOfSubmitted(Long ownerId) {
        return applications.findTimelinesExcludingCurrentStatus(ownerId, EnumSet.of(ApplicationStatus.WISHLIST))
                .stream()
                .collect(Collectors.groupingBy(TimelineEntry::applicationId, LinkedHashMap::new, Collectors.toList()))
                .values().stream().toList();
    }

    /**
     * Statuses an application really reached, from its chronological timeline. A status is ignored when the
     * very next change moves the application back to where it came from within {@link #UNDO_WINDOW}
     * (e.g. APPLIED -> INTERVIEW -> APPLIED ten minutes later). The current status always counts.
     */
    static Set<ApplicationStatus> reachedStatuses(List<TimelineEntry> timeline) {
        Set<ApplicationStatus> reached = EnumSet.noneOf(ApplicationStatus.class);
        for (int i = 0; i < timeline.size(); i++) {
            TimelineEntry entry = timeline.get(i);
            TimelineEntry next = i + 1 < timeline.size() ? timeline.get(i + 1) : null;
            boolean undone = next != null
                    && entry.fromStatus() != null
                    && next.toStatus() == entry.fromStatus()
                    && Duration.between(entry.changedAt(), next.changedAt()).compareTo(UNDO_WINDOW) < 0;
            if (!undone) {
                reached.add(entry.toStatus());
            }
        }
        return reached;
    }

    /** Applications per ISO week (Monday start) for the last {@link #WEEKS} weeks, oldest first, zero-filled. */
    private List<WeekCount> weeklyCounts(Long ownerId, LocalDate today) {
        LocalDate currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstWeek = currentWeek.minusWeeks(WEEKS - 1L);

        Map<LocalDate, Long> buckets = new LinkedHashMap<>();
        for (int i = 0; i < WEEKS; i++) {
            buckets.put(firstWeek.plusWeeks(i), 0L);
        }
        for (LocalDate applied : applications.findAppliedDatesBetween(ownerId, firstWeek, today)) {
            buckets.merge(applied.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), 1L, Long::sum);
        }
        List<WeekCount> result = new ArrayList<>(WEEKS);
        buckets.forEach((week, count) -> result.add(new WeekCount(week, count)));
        return result;
    }

    /** {@code part / whole} as a percentage with one decimal, clamped to [0, 100]. */
    static double percentage(long part, long whole) {
        if (whole <= 0 || part <= 0) {
            return 0.0;
        }
        return Math.round(Math.min(part, whole) * 1000.0 / whole) / 10.0;
    }
}
