package dev.hamza.applytrack.application;

import dev.hamza.applytrack.application.ApplicationDtos.ApplicationDetail;
import dev.hamza.applytrack.application.ApplicationDtos.ApplicationRequest;
import dev.hamza.applytrack.application.ApplicationDtos.ApplicationSummary;
import dev.hamza.applytrack.application.ApplicationDtos.InterviewRequest;
import dev.hamza.applytrack.application.ApplicationDtos.InterviewResponse;
import dev.hamza.applytrack.application.ApplicationDtos.PageResponse;
import dev.hamza.applytrack.common.BadRequestException;
import dev.hamza.applytrack.common.NotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional
public class JobApplicationService {

    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;
    private final Clock clock;

    public JobApplicationService(JobApplicationRepository applications, InterviewRepository interviews,
                                 Clock clock) {
        this.applications = applications;
        this.interviews = interviews;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationSummary> list(Long ownerId, ApplicationFilter filter, Pageable pageable) {
        return PageResponse.of(
                applications.findAll(ApplicationSpecifications.forFilter(ownerId, filter), pageable),
                ApplicationSummary::from);
    }

    @Transactional(readOnly = true)
    public ApplicationDetail get(Long ownerId, Long id) {
        return ApplicationDetail.from(load(ownerId, id));
    }

    /**
     * @param zone the user's time zone: "today" (the default applied date) is the user's calendar day,
     *             not the server's, so an application added at 00:30 in Tunis is dated that day
     */
    public ApplicationDetail create(Long ownerId, ApplicationRequest request, ZoneId zone) {
        Instant now = clock.instant();
        ApplicationStatus status = request.status() != null ? request.status() : ApplicationStatus.WISHLIST;
        JobApplication application = new JobApplication(ownerId, request.company().trim(), request.role().trim(),
                status, now);
        LocalDate appliedOn = request.appliedOn();
        if (appliedOn == null && status != ApplicationStatus.WISHLIST) {
            appliedOn = today(zone);
        }
        applyDetails(application, request, appliedOn, now);
        return ApplicationDetail.from(applications.save(application));
    }

    public ApplicationDetail update(Long ownerId, Long id, ApplicationRequest request, ZoneId zone) {
        JobApplication application = load(ownerId, id);
        checkVersion(application, request.version());
        Instant now = clock.instant();
        applyDetails(application, request, request.appliedOn(), now);
        if (request.status() != null) {
            application.moveTo(request.status(), now, today(zone));
        }
        return flushedDetail(application);
    }

    public ApplicationDetail changeStatus(Long ownerId, Long id, ApplicationStatus status, Long expectedVersion,
                                          ZoneId zone) {
        JobApplication application = load(ownerId, id);
        checkVersion(application, expectedVersion);
        application.moveTo(status, clock.instant(), today(zone));
        return flushedDetail(application);
    }

    public void delete(Long ownerId, Long id) {
        applications.delete(load(ownerId, id));
    }

    public InterviewResponse addInterview(Long ownerId, Long id, InterviewRequest request) {
        JobApplication application = load(ownerId, id);
        Interview interview = application.addInterview(request.scheduledAt(), request.type(),
                blankToNull(request.notes()), clock.instant());
        // persist (not merge) so this exact instance receives its generated id
        return InterviewResponse.from(interviews.save(interview));
    }

    public void deleteInterview(Long ownerId, Long id, Long interviewId) {
        JobApplication application = load(ownerId, id);
        if (!application.removeInterview(interviewId, clock.instant())) {
            throw new NotFoundException("Interview not found");
        }
    }

    @Transactional(readOnly = true)
    public List<String> tags(Long ownerId) {
        return applications.findDistinctTags(ownerId);
    }

    private JobApplication load(Long ownerId, Long id) {
        return applications.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Application not found"));
    }

    /**
     * Flushes first so the response carries the version JPA just incremented; otherwise the client would
     * get the old version back and its next write would be rejected as stale.
     */
    private ApplicationDetail flushedDetail(JobApplication application) {
        applications.flush();
        return ApplicationDetail.from(application);
    }

    /**
     * Rejects a write based on a stale read. JPA's @Version only protects the few milliseconds between our own
     * read and write; comparing with the version the client saw also catches edits made in another tab
     * minutes ago. Both cases end up as an OptimisticLockingFailureException, mapped to 409.
     */
    private static void checkVersion(JobApplication application, Long expectedVersion) {
        if (expectedVersion == null) {
            throw new BadRequestException("'version' is required: send the version you last read");
        }
        if (application.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(JobApplication.class, application.getId());
        }
    }

    private void applyDetails(JobApplication application, ApplicationRequest r, LocalDate appliedOn, Instant now) {
        application.updateDetails(r.company().trim(), r.role().trim(), blankToNull(r.location()),
                blankToNull(r.url()), blankToNull(r.salaryRange()), appliedOn, r.followUpOn(),
                blankToNull(r.notes()), normalizeTags(r.tags()), now);
    }

    private LocalDate today(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    static Set<String> normalizeTags(List<String> tags) {
        Set<String> result = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                if (StringUtils.hasText(tag)) {
                    result.add(tag.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
