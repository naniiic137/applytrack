package dev.hamza.applytrack.stats;

import dev.hamza.applytrack.application.ApplicationStatus;
import dev.hamza.applytrack.application.InterviewRepository;
import dev.hamza.applytrack.application.JobApplicationRepository;
import dev.hamza.applytrack.application.StatusCount;
import dev.hamza.applytrack.stats.StatsResponse.FollowUp;
import dev.hamza.applytrack.stats.StatsResponse.UpcomingInterview;
import dev.hamza.applytrack.stats.StatsResponse.WeekCount;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StatsService {

    static final int WEEKS = 12;
    static final int FOLLOW_UP_WINDOW_DAYS = 14;
    private static final int LIST_LIMIT = 8;

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

        long responded = applications.countEverReached(ownerId, ApplicationStatus.RESPONSES);
        long interviewed = applications.countEverReached(ownerId, ApplicationStatus.INTERVIEWED);

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

    static double percentage(long part, long whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return Math.round(part * 1000.0 / whole) / 10.0;
    }
}
