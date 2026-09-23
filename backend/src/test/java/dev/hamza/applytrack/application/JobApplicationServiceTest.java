package dev.hamza.applytrack.application;

import dev.hamza.applytrack.application.ApplicationDtos.ApplicationDetail;
import dev.hamza.applytrack.application.ApplicationDtos.ApplicationRequest;
import dev.hamza.applytrack.application.ApplicationDtos.ApplicationSummary;
import dev.hamza.applytrack.application.ApplicationDtos.PageResponse;
import dev.hamza.applytrack.common.NotFoundException;
import dev.hamza.applytrack.user.User;
import dev.hamza.applytrack.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
        service = new JobApplicationService(repository, interviews, Clock.fixed(NOW, ZoneOffset.UTC));
        owner = users.save(new User("owner@example.com", "x", "Owner", NOW)).getId();
        otherOwner = users.save(new User("other@example.com", "x", "Other", NOW)).getId();
    }

    private static ApplicationRequest request(String company, ApplicationStatus status, List<String> tags) {
        return new ApplicationRequest(company, "Developer", null, null, null, status, null, null, null, tags);
    }

    @Test
    void statusChangesAreRecordedInOrderAndSetAppliedOnOnce() {
        Long id = service.create(owner, request("Acme", null, List.of())).id();

        service.changeStatus(owner, id, ApplicationStatus.APPLIED);
        service.changeStatus(owner, id, ApplicationStatus.INTERVIEW);
        service.changeStatus(owner, id, ApplicationStatus.INTERVIEW); // no-op
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
    void tagsAreTrimmedLowercasedAndDeduplicated() {
        ApplicationDetail detail = service.create(owner, request("Acme", null, List.of(" Java", "JAVA", "Spring ", "")));

        assertThat(detail.tags()).containsExactly("java", "spring");
    }

    @Test
    void anotherOwnersIdIsReportedAsNotFound() {
        Long id = service.create(owner, request("Acme", null, List.of())).id();

        assertThatThrownBy(() -> service.get(otherOwner, id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.changeStatus(otherOwner, id, ApplicationStatus.OFFER))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void tagFilterDoesNotDuplicateRowsAndStaysOwnerScoped() {
        service.create(owner, request("Multi-tag", ApplicationStatus.APPLIED, List.of("java", "spring", "react")));
        service.create(owner, request("Java only", ApplicationStatus.APPLIED, List.of("java")));
        service.create(otherOwner, request("Someone else", ApplicationStatus.APPLIED, List.of("java")));
        em.flush();

        PageResponse<ApplicationSummary> page = service.list(owner, new ApplicationFilter(null, null, "JAVA"),
                PageRequest.of(0, 10, Sort.by("company")));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(ApplicationSummary::company).containsExactly("Java only", "Multi-tag");
    }

    @Test
    void textSearchTreatsLikeWildcardsLiterally() {
        service.create(owner, request("100% Remote Inc", null, List.of()));
        service.create(owner, request("Other", null, List.of()));
        em.flush();

        PageResponse<ApplicationSummary> page = service.list(owner, new ApplicationFilter(null, "100%", null),
                PageRequest.of(0, 10));

        assertThat(page.content()).extracting(ApplicationSummary::company).containsExactly("100% Remote Inc");
    }
}
