package dev.hamza.applytrack.stats;

import dev.hamza.applytrack.application.ApplicationStatus;
import dev.hamza.applytrack.application.InterviewRepository;
import dev.hamza.applytrack.application.InterviewType;
import dev.hamza.applytrack.application.JobApplication;
import dev.hamza.applytrack.application.JobApplicationRepository;
import dev.hamza.applytrack.user.User;
import dev.hamza.applytrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StatsServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-04T09:00:00Z"); // Wednesday
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 4);
    private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 3, 2);

    @Autowired
    private JobApplicationRepository applications;
    @Autowired
    private InterviewRepository interviews;
    @Autowired
    private UserRepository users;
    @Autowired
    private TestEntityManager em;

    private StatsService stats;
    private Long owner;

    @BeforeEach
    void setUp() {
        stats = new StatsService(applications, interviews, Clock.fixed(NOW, ZoneOffset.UTC));
        owner = users.save(new User("stats@example.com", "x", "Stats", NOW)).getId();
    }

    private JobApplication app(String company, LocalDate appliedOn, ApplicationStatus... path) {
        JobApplication a = new JobApplication(owner, company, "Dev", ApplicationStatus.WISHLIST,
                NOW.minus(Duration.ofDays(60)));
        for (ApplicationStatus s : path) {
            a.moveTo(s, NOW, appliedOn);
        }
        return applications.save(a);
    }

    @Test
    void appliedDatesAreBucketedIntoZeroFilledMondayWeeks() {
        app("This week (Mon)", THIS_MONDAY, ApplicationStatus.APPLIED);
        app("This week (Wed)", TODAY, ApplicationStatus.APPLIED);
        app("Last week (Sun)", THIS_MONDAY.minusDays(1), ApplicationStatus.APPLIED);
        app("Too old", THIS_MONDAY.minusWeeks(12), ApplicationStatus.APPLIED);
        em.flush();

        List<StatsResponse.WeekCount> weeks = stats.forUser(owner).applicationsPerWeek();

        assertThat(weeks).hasSize(StatsService.WEEKS);
        assertThat(weeks.getFirst().weekStart()).isEqualTo(THIS_MONDAY.minusWeeks(11));
        assertThat(weeks.getLast()).isEqualTo(new StatsResponse.WeekCount(THIS_MONDAY, 2));
        assertThat(weeks.get(10)).isEqualTo(new StatsResponse.WeekCount(THIS_MONDAY.minusWeeks(1), 1));
        assertThat(weeks.stream().mapToLong(StatsResponse.WeekCount::count).sum()).isEqualTo(3);
    }

    @Test
    void responseRateUsesTheTimelineNotJustTheCurrentStatus() {
        app("Wishlist only", null);
        app("Waiting", TODAY, ApplicationStatus.APPLIED);
        app("Rejected", TODAY, ApplicationStatus.APPLIED, ApplicationStatus.REJECTED);
        app("Interviewed then ghosted", TODAY, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW,
                ApplicationStatus.GHOSTED);
        em.flush();

        StatsResponse result = stats.forUser(owner);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.submitted()).isEqualTo(3);
        assertThat(result.responseRate()).isEqualTo(66.7);  // rejected + interviewed-then-ghosted
        assertThat(result.interviewRate()).isEqualTo(33.3); // only the one that reached INTERVIEW
    }

    @Test
    void followUpsIncludeOverdueAndSkipClosedApplications() {
        JobApplication overdue = app("Overdue", TODAY.minusDays(10), ApplicationStatus.APPLIED);
        overdue.updateDetails("Overdue", "Dev", null, null, null, TODAY.minusDays(10), TODAY.minusDays(1), null,
                List.of(), NOW);
        JobApplication soon = app("Soon", TODAY.minusDays(5), ApplicationStatus.APPLIED);
        soon.updateDetails("Soon", "Dev", null, null, null, TODAY.minusDays(5), TODAY.plusDays(2), null,
                List.of(), NOW);
        JobApplication tooFar = app("Too far", TODAY, ApplicationStatus.APPLIED);
        tooFar.updateDetails("Too far", "Dev", null, null, null, TODAY, TODAY.plusDays(30), null, List.of(), NOW);
        JobApplication closed = app("Closed", TODAY.minusDays(20), ApplicationStatus.APPLIED,
                ApplicationStatus.REJECTED);
        closed.updateDetails("Closed", "Dev", null, null, null, TODAY.minusDays(20), TODAY.plusDays(1), null,
                List.of(), NOW);
        JobApplication withInterview = app("Interviewing", TODAY.minusDays(7), ApplicationStatus.APPLIED,
                ApplicationStatus.INTERVIEW);
        withInterview.addInterview(NOW.plus(Duration.ofDays(2)), InterviewType.TECHNICAL, null, NOW);
        withInterview.addInterview(NOW.minus(Duration.ofDays(2)), InterviewType.PHONE_SCREEN, null, NOW);
        em.flush();

        StatsResponse result = stats.forUser(owner);

        assertThat(result.upcomingFollowUps())
                .extracting(StatsResponse.FollowUp::company, StatsResponse.FollowUp::overdue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Overdue", true),
                        org.assertj.core.groups.Tuple.tuple("Soon", false));
        assertThat(result.upcomingInterviews())
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.company()).isEqualTo("Interviewing");
                    assertThat(i.type()).isEqualTo(InterviewType.TECHNICAL);
                });
    }

    @Test
    void percentageHandlesZeroAndRounding() {
        assertThat(StatsService.percentage(0, 0)).isZero();
        assertThat(StatsService.percentage(1, 3)).isEqualTo(33.3);
        assertThat(StatsService.percentage(2, 3)).isEqualTo(66.7);
        assertThat(StatsService.percentage(5, 5)).isEqualTo(100.0);
    }
}
