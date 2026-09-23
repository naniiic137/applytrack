package dev.hamza.applytrack.application;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 120)
    private String company;

    @Column(nullable = false, length = 120)
    private String role;

    @Column(length = 120)
    private String location;

    @Column(length = 500)
    private String url;

    @Column(name = "salary_range", length = 80)
    private String salaryRange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "applied_on")
    private LocalDate appliedOn;

    @Column(name = "follow_up_on")
    private LocalDate followUpOn;

    @Column(length = 4000)
    private String notes;

    @ElementCollection
    @CollectionTable(name = "application_tags", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "tag", length = 40)
    private Set<String> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC, id ASC")
    private List<StatusChange> statusChanges = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("scheduledAt ASC")
    private List<Interview> interviews = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {
    }

    /**
     * Creates an application and writes the first timeline entry (null -> initial status).
     */
    public JobApplication(Long ownerId, String company, String role, ApplicationStatus status, Instant now) {
        this.ownerId = Objects.requireNonNull(ownerId);
        this.company = company;
        this.role = role;
        this.status = Objects.requireNonNull(status);
        this.createdAt = now;
        this.updatedAt = now;
        this.statusChanges.add(new StatusChange(this, null, status, now));
    }

    /**
     * Moves the application to a new status and records the transition in the timeline.
     * Moving to APPLIED fills in {@code appliedOn} if it was empty.
     *
     * @return false when the status did not actually change (no timeline entry is written)
     */
    public boolean moveTo(ApplicationStatus newStatus, Instant at, LocalDate today) {
        Objects.requireNonNull(newStatus);
        if (newStatus == status) {
            return false;
        }
        statusChanges.add(new StatusChange(this, status, newStatus, at));
        this.status = newStatus;
        if (newStatus != ApplicationStatus.WISHLIST && appliedOn == null) {
            this.appliedOn = today;
        }
        touch(at);
        return true;
    }

    public Interview addInterview(Instant scheduledAt, InterviewType type, String notes, Instant now) {
        Interview interview = new Interview(this, scheduledAt, type, notes, now);
        interviews.add(interview);
        touch(now);
        return interview;
    }

    public boolean removeInterview(Long interviewId, Instant now) {
        boolean removed = interviews.removeIf(i -> Objects.equals(i.getId(), interviewId));
        if (removed) {
            touch(now);
        }
        return removed;
    }

    public void updateDetails(String company, String role, String location, String url, String salaryRange,
                              LocalDate appliedOn, LocalDate followUpOn, String notes, Collection<String> tags,
                              Instant now) {
        this.company = company;
        this.role = role;
        this.location = location;
        this.url = url;
        this.salaryRange = salaryRange;
        this.appliedOn = appliedOn;
        this.followUpOn = followUpOn;
        this.notes = notes;
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getCompany() {
        return company;
    }

    public String getRole() {
        return role;
    }

    public String getLocation() {
        return location;
    }

    public String getUrl() {
        return url;
    }

    public String getSalaryRange() {
        return salaryRange;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public LocalDate getFollowUpOn() {
        return followUpOn;
    }

    public String getNotes() {
        return notes;
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public List<StatusChange> getStatusChanges() {
        return Collections.unmodifiableList(statusChanges);
    }

    public List<Interview> getInterviews() {
        return Collections.unmodifiableList(interviews);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
