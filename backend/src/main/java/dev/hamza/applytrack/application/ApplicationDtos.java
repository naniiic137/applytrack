package dev.hamza.applytrack.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record ApplicationRequest(
            @NotBlank @Size(max = 120) String company,
            @NotBlank @Size(max = 120) String role,
            @Size(max = 120) String location,
            @Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String url,
            @Size(max = 80) String salaryRange,
            ApplicationStatus status,
            LocalDate appliedOn,
            LocalDate followUpOn,
            @Size(max = 4000) String notes,
            @Size(max = 10, message = "at most 10 tags") List<@NotBlank @Size(max = 40) String> tags,
            @Schema(description = "Required on update: the version you last read (optimistic locking)")
            Long version) {

        @AssertTrue(message = "follow-up date cannot be before the applied date")
        boolean isFollowUpAfterApplied() {
            return appliedOn == null || followUpOn == null || !followUpOn.isBefore(appliedOn);
        }
    }

    /**
     * @param version the version the client last read; a mismatch means someone else changed the
     *                application in the meantime and the request is rejected with 409
     */
    public record StatusUpdateRequest(@NotNull ApplicationStatus status, @NotNull Long version) {
    }

    public record InterviewRequest(
            @NotNull Instant scheduledAt,
            @NotNull InterviewType type,
            @Size(max = 2000) String notes) {
    }

    public record ApplicationSummary(
            Long id,
            String company,
            String role,
            String location,
            String salaryRange,
            ApplicationStatus status,
            LocalDate appliedOn,
            LocalDate followUpOn,
            List<String> tags,
            int interviewCount,
            Instant updatedAt,
            long version) {

        static ApplicationSummary from(JobApplication a) {
            return new ApplicationSummary(a.getId(), a.getCompany(), a.getRole(), a.getLocation(),
                    a.getSalaryRange(), a.getStatus(), a.getAppliedOn(), a.getFollowUpOn(),
                    a.getTags().stream().sorted().toList(), a.getInterviews().size(), a.getUpdatedAt(),
                    a.getVersion());
        }
    }

    public record StatusChangeResponse(Long id, ApplicationStatus fromStatus, ApplicationStatus toStatus,
                                       Instant changedAt) {

        static StatusChangeResponse from(StatusChange c) {
            return new StatusChangeResponse(c.getId(), c.getFromStatus(), c.getToStatus(), c.getChangedAt());
        }
    }

    public record InterviewResponse(Long id, Instant scheduledAt, InterviewType type, String notes) {

        static InterviewResponse from(Interview i) {
            return new InterviewResponse(i.getId(), i.getScheduledAt(), i.getType(), i.getNotes());
        }
    }

    public record ApplicationDetail(
            Long id,
            String company,
            String role,
            String location,
            String url,
            String salaryRange,
            ApplicationStatus status,
            LocalDate appliedOn,
            LocalDate followUpOn,
            String notes,
            List<String> tags,
            List<StatusChangeResponse> timeline,
            List<InterviewResponse> interviews,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static ApplicationDetail from(JobApplication a) {
            return new ApplicationDetail(a.getId(), a.getCompany(), a.getRole(), a.getLocation(), a.getUrl(),
                    a.getSalaryRange(), a.getStatus(), a.getAppliedOn(), a.getFollowUpOn(), a.getNotes(),
                    a.getTags().stream().sorted().toList(),
                    a.getStatusChanges().stream().map(StatusChangeResponse::from).toList(),
                    a.getInterviews().stream().map(InterviewResponse::from).toList(),
                    a.getCreatedAt(), a.getUpdatedAt(), a.getVersion());
        }
    }

    /** Stable JSON shape for paged results (instead of serializing Spring's PageImpl). */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                    page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
