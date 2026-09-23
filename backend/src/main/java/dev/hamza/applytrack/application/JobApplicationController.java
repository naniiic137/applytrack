package dev.hamza.applytrack.application;

import dev.hamza.applytrack.application.ApplicationDtos.ApplicationDetail;
import dev.hamza.applytrack.application.ApplicationDtos.ApplicationRequest;
import dev.hamza.applytrack.application.ApplicationDtos.ApplicationSummary;
import dev.hamza.applytrack.application.ApplicationDtos.InterviewRequest;
import dev.hamza.applytrack.application.ApplicationDtos.InterviewResponse;
import dev.hamza.applytrack.application.ApplicationDtos.PageResponse;
import dev.hamza.applytrack.application.ApplicationDtos.StatusUpdateRequest;
import dev.hamza.applytrack.auth.AuthUser;
import dev.hamza.applytrack.common.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications", description = "Job applications, status changes and interviews")
public class JobApplicationController {

    private static final Set<String> SORTABLE =
            Set.of("updatedAt", "createdAt", "appliedOn", "followUpOn", "company", "role", "status");

    private final JobApplicationService service;

    public JobApplicationController(JobApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List applications with optional status / text / tag filters, paged and sorted")
    public PageResponse<ApplicationSummary> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(name = "status", required = false) List<ApplicationStatus> statuses,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String tag,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed: " + SORTABLE);
            }
        }
        return service.list(user.id(), new ApplicationFilter(statuses, q, tag), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Application detail including status timeline and interviews")
    public ApplicationDetail get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.get(user.id(), id);
    }

    @PostMapping
    public ResponseEntity<ApplicationDetail> create(@AuthenticationPrincipal AuthUser user,
                                                    @Valid @RequestBody ApplicationRequest request) {
        ApplicationDetail created = service.create(user.id(), request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ApplicationDetail update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                    @Valid @RequestBody ApplicationRequest request) {
        return service.update(user.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an application to another status (records a timeline entry)")
    public ApplicationDetail changeStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                          @Valid @RequestBody StatusUpdateRequest request) {
        return service.changeStatus(user.id(), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        service.delete(user.id(), id);
    }

    @PostMapping("/{id}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewResponse addInterview(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                          @Valid @RequestBody InterviewRequest request) {
        return service.addInterview(user.id(), id, request);
    }

    @DeleteMapping("/{id}/interviews/{interviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInterview(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                @PathVariable Long interviewId) {
        service.deleteInterview(user.id(), id, interviewId);
    }

    @GetMapping("/tags")
    @Operation(summary = "All distinct tags used by the current user")
    public List<String> tags(@AuthenticationPrincipal AuthUser user) {
        return service.tags(user.id());
    }
}
