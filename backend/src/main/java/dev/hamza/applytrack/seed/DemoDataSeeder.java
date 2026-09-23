package dev.hamza.applytrack.seed;

import dev.hamza.applytrack.application.ApplicationStatus;
import dev.hamza.applytrack.application.InterviewType;
import dev.hamza.applytrack.application.JobApplication;
import dev.hamza.applytrack.application.JobApplicationRepository;
import dev.hamza.applytrack.user.User;
import dev.hamza.applytrack.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static dev.hamza.applytrack.application.ApplicationStatus.APPLIED;
import static dev.hamza.applytrack.application.ApplicationStatus.GHOSTED;
import static dev.hamza.applytrack.application.ApplicationStatus.INTERVIEW;
import static dev.hamza.applytrack.application.ApplicationStatus.OFFER;
import static dev.hamza.applytrack.application.ApplicationStatus.REJECTED;
import static dev.hamza.applytrack.application.ApplicationStatus.WISHLIST;

/**
 * Creates a demo account with a realistic, date-relative job search so the UI has something to show.
 * All companies are fictional. Runs only when app.seed.enabled=true (the dev profile) and only once.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@applytrack.dev";
    public static final String DEMO_PASSWORD = "demo1234";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final JobApplicationRepository applications;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(UserRepository users, JobApplicationRepository applications,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.applications = applications;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * @param appliedDaysAgo days between the application date and today (for WISHLIST: when it was saved)
     * @param followUpIn     follow-up date relative to today, or null
     * @param viaInterview   for REJECTED / GHOSTED: whether the application got to the interview stage first
     */
    private record Seed(String company, String role, String location, String salary, List<String> tags,
                        int appliedDaysAgo, ApplicationStatus finalStatus, Integer followUpIn, boolean viaInterview,
                        String notes) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("Northwind Labs", "Junior Full-Stack Developer", "Tunis (hybrid)", "2,800-3,400 TND",
                    List.of("react", "spring"), 20, INTERVIEW, 2, false,
                    "Referred by a former classmate. Stack: React, Spring Boot, PostgreSQL."),
            new Seed("Cedar & Pine Software", "Frontend Developer (React)", "Remote (EU)", "EUR 32-38k",
                    List.of("react", "typescript", "remote"), 27, OFFER, 3, false,
                    "Offer received - compare with Northwind before replying."),
            new Seed("Halfmoon Analytics", "Java Backend Developer", "Sfax", "2,500-3,000 TND",
                    List.of("java", "spring"), 34, INTERVIEW, 5, false,
                    "Technical round focused on JPA and REST design."),
            new Seed("Bluefin Payments", "Software Engineer I", "Tunis", null,
                    List.of("java", "fintech"), 12, APPLIED, -1, false,
                    "Applied through the careers page. Follow up with the recruiter on LinkedIn."),
            new Seed("Atlas Mobility", "Full-Stack Engineer", "Remote", "EUR 35-42k",
                    List.of("react", "node", "remote"), 9, APPLIED, 4, false, null),
            new Seed("Quarry Health", "Junior Software Engineer", "Paris (hybrid)", "EUR 38-42k",
                    List.of("typescript", "java"), 41, REJECTED, null, true,
                    "Rejected after the final round - asked for feedback."),
            new Seed("Lumen Robotics", "React Developer", "Remote (EU)", null,
                    List.of("react", "remote"), 46, GHOSTED, null, false,
                    "No reply after two follow-ups."),
            new Seed("Oakridge Retail Tech", "Backend Developer (Spring)", "Tunis", "2,600-3,200 TND",
                    List.of("java", "spring"), 16, APPLIED, 1, false, null),
            new Seed("Saltwater Studio", "Frontend Engineer", "Remote", null,
                    List.of("react", "design-systems"), 3, WISHLIST, 6, false,
                    "Great design culture. Tailor the portfolio before applying."),
            new Seed("Kestrel Logistics", "Full-Stack Developer", "Sousse", "2,400-2,900 TND",
                    List.of("angular", "spring"), 52, REJECTED, null, false,
                    "Automated rejection email."),
            new Seed("Brightpath Education", "Junior Web Developer", "Remote", "EUR 28-33k",
                    List.of("react", "remote"), 0, APPLIED, 8, false, null),
            new Seed("Verdant Energy", "Software Developer - Internal Tools", "Tunis (hybrid)", null,
                    List.of("java", "react"), 23, INTERVIEW, 0, false,
                    "HR screen went well; waiting on the technical interview slot."),
            new Seed("Mosaic Cloud", "Platform Engineer (Junior)", "Berlin (remote)", "EUR 45-50k",
                    List.of("java", "docker"), 58, GHOSTED, null, true, null),
            new Seed("Pinecone Games", "Web Developer", "Remote", null,
                    List.of("typescript", "remote"), 2, WISHLIST, null, false, null),
            new Seed("Harbor Insurance", "Java Developer", "Tunis", "2,700-3,300 TND",
                    List.of("java", "spring", "fintech"), 30, REJECTED, null, true,
                    "Went with a more senior candidate."),
            new Seed("Tidewater Media", "React Native / Web Developer", "Remote (EU)", null,
                    List.of("react", "mobile", "remote"), 13, APPLIED, 3, false, null),
            new Seed("Sable Security", "Junior Full-Stack Developer", "Tunis", null,
                    List.of("react", "spring", "security"), 4, WISHLIST, 10, false,
                    "Ask about the mentorship programme."),
            new Seed("Orchard HR", "Software Engineer (Graduate)", "Remote", "EUR 30-34k",
                    List.of("typescript", "node"), 37, GHOSTED, null, false, null),
            new Seed("Granite Data", "Backend Engineer", "Sfax (hybrid)", null,
                    List.of("java", "postgresql"), 19, APPLIED, -2, false,
                    "Recruiter said they would reply within two weeks."),
            new Seed("Aurora Travel", "Frontend Developer", "Tunis", "2,300-2,800 TND",
                    List.of("react", "typescript"), 65, REJECTED, null, false, null),
            new Seed("Copperleaf Commerce", "Full-Stack Developer", "Remote (EU)", "EUR 34-40k",
                    List.of("react", "spring", "remote"), 8, INTERVIEW, 6, false,
                    "Take-home: build a small REST API with tests."),
            new Seed("Juniper Civic", "Web Developer (Public Sector)", "Tunis", null,
                    List.of("java", "angular"), 71, GHOSTED, null, false, null),
            new Seed("Riverstone Bank", "Graduate Java Engineer", "Tunis", null,
                    List.of("java", "fintech"), 1, WISHLIST, 12, false, null),
            new Seed("Meridian Maps", "Frontend Engineer (Maps)", "Remote", "EUR 36-44k",
                    List.of("react", "typescript", "remote"), 44, REJECTED, null, true, null)
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        User demo = users.save(new User(DEMO_EMAIL, passwordEncoder.encode(DEMO_PASSWORD), "Demo User",
                at(today.minusDays(80), 9)));

        for (Seed seed : SEEDS) {
            applications.save(build(demo.getId(), seed, today));
        }
        log.info("Seeded demo account {} with {} applications", DEMO_EMAIL, SEEDS.size());
    }

    private JobApplication build(Long ownerId, Seed s, LocalDate today) {
        LocalDate applied = today.minusDays(s.appliedDaysAgo());
        LocalDate saved = s.finalStatus() == WISHLIST ? applied : applied.minusDays(2 + s.appliedDaysAgo() % 3);

        JobApplication app = new JobApplication(ownerId, s.company(), s.role(), WISHLIST, at(saved, 10));
        app.updateDetails(s.company(), s.role(), s.location(), "https://careers.example.com/" + slug(s.company()),
                s.salary(), null, s.followUpIn() == null ? null : today.plusDays(s.followUpIn()), s.notes(),
                s.tags(), at(saved, 10));
        if (s.finalStatus() == WISHLIST) {
            return app;
        }

        app.moveTo(APPLIED, at(applied, 11), applied);
        LocalDate interviewDay = applied.plusDays(Math.max(3, s.appliedDaysAgo() / 3));

        switch (s.finalStatus()) {
            case INTERVIEW -> {
                app.moveTo(INTERVIEW, at(interviewDay.minusDays(2), 14), applied);
                app.addInterview(at(interviewDay, 13), InterviewType.PHONE_SCREEN,
                        "30 min with the recruiter: background, motivation, salary expectations.",
                        at(interviewDay.minusDays(2), 14));
                if (s.followUpIn() != null) {
                    app.addInterview(at(today.plusDays(s.followUpIn() + 1L), 10), InterviewType.TECHNICAL,
                            "Live coding + system design basics. Revise JPA fetching and React state.",
                            at(interviewDay, 16));
                }
            }
            case OFFER -> {
                app.moveTo(INTERVIEW, at(interviewDay.minusDays(2), 14), applied);
                app.addInterview(at(interviewDay, 13), InterviewType.PHONE_SCREEN, "Recruiter call.",
                        at(interviewDay.minusDays(2), 14));
                app.addInterview(at(interviewDay.plusDays(6), 15), InterviewType.TECHNICAL,
                        "Pair-programming on a React component; went well.", at(interviewDay, 16));
                app.moveTo(OFFER, at(today.minusDays(2), 17), applied);
            }
            case REJECTED, GHOSTED -> {
                if (s.viaInterview()) {
                    app.moveTo(INTERVIEW, at(interviewDay.minusDays(2), 14), applied);
                    app.addInterview(at(interviewDay, 13), InterviewType.TECHNICAL, null,
                            at(interviewDay.minusDays(2), 14));
                }
                LocalDate closed = applied.plusDays(Math.min(s.appliedDaysAgo() - 1, 18));
                app.moveTo(s.finalStatus(), at(closed, 9), applied);
            }
            default -> {
                // APPLIED: nothing more to do
            }
        }
        return app;
    }

    private static Instant at(LocalDate date, int hour) {
        return date.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC);
    }

    private static String slug(String company) {
        return company.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
