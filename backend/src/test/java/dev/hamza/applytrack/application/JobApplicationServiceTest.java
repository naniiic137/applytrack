package dev.hamza.applytrack.application;

import dev.hamza.applytrack.application.ApplicationDtos.ApplicationDetail;
import dev.hamza.applytrack.application.ApplicationDtos.ApplicationRequest;
import dev.hamza.applytrack.application.ApplicationDtos.ApplicationSummary;
import dev.hamza.applytrack.application.ApplicationDtos.PageResponse;
import dev.hamza.applytrack.common.BadRequestException;
import dev.hamza.applytrack.common.FieldValidationException;
import dev.hamza.applytrack.common.NotFoundException;
import dev.hamza.applytrack.user.User;
import dev.hamza.applytrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Service + repository test against the real schema (Flyway migrations on H2 in PostgreSQL mode),
 * with a fixed clock so dates are deterministic.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class JobApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-04T09:00:00Z"); // a Wednesday
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 4);
    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis"); // UTC+1, no DST

    @Autowired
    private JobApplicationRepository repository;
    @Autowired
    private InterviewRepository interviews;
    @Autowired
    private UserRepository users;
    @Autowired
    private TestEntityManager em;

    private JobApplicationService service;
    private Long owner;
    private Long otherOwner;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(repository, interviews, Clock.fixed(NOW, UTC));
        owner = users.save(new User("owner@example.com", "x", "Owner", NOW)).getId();
        otherOwner = users.save(new User("other@example.com", "x", "Other", NOW)).getId();
    }

    private static ApplicationRequest request(String company, ApplicationStatus status, List<String> tags) {
        return new ApplicationRequest(company, "Developer", null, null, null, status, null, null, null, tags, null);
    }

    /** Version as the client would see it after the previous request committed. */
    private long version(Long id) {
        em.flush();
        return service.get(owner, id).version();
    }

    @Test
    void statusChangesAreRecordedInOrderAndSetAppliedOnOnce() {
        Long id = service.create(owner, request("Acme", null, List.of()), UTC).id();

        service.changeStatus(owner, id, ApplicationStatus.APPLIED, version(id), UTC);
        service.changeStatus(owner, id, ApplicationStatus.INTERVIEW, version(id), UTC);
        service.changeStatus(owner, id, ApplicationStatus.INTERVIEW, version(id), UTC); // no-op
        em.flush();
        em.clear();

        ApplicationDetail detail = service.get(owner, id);
        assertThat(detail.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(detail.appliedOn()).isEqualTo(TODAY);
        assertThat(detail.timeline())
                .extracting(ApplicationDtos.StatusChangeResponse::fromStatus, ApplicationDtos.StatusChangeResponse::toStatus)
                .containsExactly(
                        tuple(null, ApplicationStatus.WISHLIST),
                        tuple(ApplicationStatus.WISHLIST, ApplicationStatus.APPLIED),
                        tuple(ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW));
    }

    @Test
    void staleOrMissingVersionsAreRejected() {
        Long id = service.create(owner, request("Acme", null, List.of()), UTC).id();
        service.changeStatus(owner, id, ApplicationStatus.APPLIED, 0L, UTC);
        em.flush();

        assertThatThrownBy(() -> service.changeStatus(owner, id, ApplicationStatus.OFFER, 0L, UTC))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThatThrownBy(() -> service.changeStatus(owner, id, ApplicationStatus.OFFER, null, UTC))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update(owner, id, request("Acme 2", null, List.of()), UTC))
                .isInstanceOf(BadRequestException.class);
        assertThat(service.get(owner, id).status()).isEqualTo(ApplicationStatus.APPLIED);
    }

    @Test
    void defaultAppliedDateIsTheUsersCalendarDayNotTheServers() {
        // 23:30 UTC on 4 March is already 00:30 on 5 March in Tunis
        JobApplicationService lateNight = new JobApplicationService(repository, interviews,
                Clock.fixed(Instant.parse("2026-03-04T23:30:00Z"), UTC));

        ApplicationDetail inTunis = lateNight.create(owner, request("T", ApplicationStatus.APPLIED, List.of()), TUNIS);
        ApplicationDetail inUtc = lateNight.create(owner, request("U", ApplicationStatus.APPLIED, List.of()), UTC);
        Long dragged = lateNight.create(owner, request("Dragged", null, List.of()), TUNIS).id();
        em.flush();

        assertThat(inTunis.appliedOn()).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(inUtc.appliedOn()).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(lateNight.changeStatus(owner, dragged, ApplicationStatus.APPLIED, 0L, TUNIS).appliedOn())
                .isEqualTo(LocalDate.of(2026, 3, 5));
    }

    @Test
    void followUpBeforeTheDefaultedAppliedDateIsRejected() {
        ApplicationRequest applied = new ApplicationRequest("Acme", "Dev", null, null, null, ApplicationStatus.APPLIED,
                null, TODAY.minusDays(1), null, List.of(), null);

        // appliedOn is empty in the request, so the DTO check passes; the service fills in today and re-checks
        assertThatThrownBy(() -> service.create(owner, applied, UTC))
                .isInstanceOf(FieldValidationException.class)
                .hasMessage("follow-up date cannot be before the applied date");

        Long id = service.create(owner, new ApplicationRequest("Acme", "Dev", null, null, null, null,
                null, TODAY.minusDays(1), null, List.of(), null), UTC).id(); // Wishlist: no applied date, fine
        em.flush();
        ApplicationRequest moveToApplied = new ApplicationRequest("Acme", "Dev", null, null, null,
                ApplicationStatus.APPLIED, null, TODAY.minusDays(1), null, List.of(), 0L);
        assertThatThrownBy(() -> service.update(owner, id, moveToApplied, UTC))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void tagsAreTrimmedLowercasedAndDeduplicated() {
        ApplicationDetail detail = service.create(owner,
                request("Acme", null, List.of(" Java", "JAVA", "Spring ", "")), UTC);

        assertThat(detail.tags()).containsExactly("java", "spring");
    }

    @Test
    void anotherOwnersIdIsReportedAsNotFound() {
        Long id = service.create(owner, request("Acme", null, List.of()), UTC).id();

        assertThatThrownBy(() -> service.get(otherOwner, id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.changeStatus(otherOwner, id, ApplicationStatus.OFFER, 0L, UTC))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void tagFilterDoesNotDuplicateRowsAndStaysOwnerScoped() {
        service.create(owner, request("Multi-tag", ApplicationStatus.APPLIED, List.of("java", "spring", "react")), UTC);
        service.create(owner, request("Java only", ApplicationStatus.APPLIED, List.of("java")), UTC);
        service.create(otherOwner, request("Someone else", ApplicationStatus.APPLIED, List.of("java")), UTC);
        em.flush();

        PageResponse<ApplicationSummary> page = service.list(owner, new ApplicationFilter(null, null, "JAVA"),
                PageRequest.of(0, 10, Sort.by("company")));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(ApplicationSummary::company).containsExactly("Java only", "Multi-tag");
    }

    @Test
    void textSearchTreatsLikeWildcardsLiterally() {
        service.create(owner, request("100% Remote Inc", null, List.of()), UTC);
        service.create(owner, request("Other", null, List.of()), UTC);
        em.flush();

        PageResponse<ApplicationSummary> page = service.list(owner, new ApplicationFilter(null, "100%", null),
                PageRequest.of(0, 10));

        assertThat(page.content()).extracting(ApplicationSummary::company).containsExactly("100% Remote Inc");
    }
}
