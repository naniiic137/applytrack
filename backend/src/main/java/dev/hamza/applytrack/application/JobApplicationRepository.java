package dev.hamza.applytrack.application;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long>,
        JpaSpecificationExecutor<JobApplication> {

    /** Every lookup by id is scoped to the owner: another user's id behaves exactly like a missing one. */
    Optional<JobApplication> findByIdAndOwnerId(Long id, Long ownerId);

    @Query("""
            select new dev.hamza.applytrack.application.StatusCount(a.status, count(a))
            from JobApplication a
            where a.ownerId = :ownerId
            group by a.status
            """)
    List<StatusCount> countByStatus(@Param("ownerId") Long ownerId);

    @Query("""
            select a.appliedOn from JobApplication a
            where a.ownerId = :ownerId and a.appliedOn >= :from and a.appliedOn <= :to
            """)
    List<LocalDate> findAppliedDatesBetween(@Param("ownerId") Long ownerId, @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("""
            select a from JobApplication a
            where a.ownerId = :ownerId
              and a.followUpOn is not null
              and a.followUpOn <= :until
              and a.status in :statuses
            order by a.followUpOn asc, a.id asc
            """)
    List<JobApplication> findFollowUpsDueBy(@Param("ownerId") Long ownerId, @Param("until") LocalDate until,
                                            @Param("statuses") Collection<ApplicationStatus> statuses, Limit limit);

    /**
     * Timelines of the owner's applications whose current status is not one of {@code excluded},
     * grouped by application and in chronological order.
     */
    @Query("""
            select new dev.hamza.applytrack.application.TimelineEntry(a.id, sc.fromStatus, sc.toStatus, sc.changedAt)
            from StatusChange sc join sc.application a
            where a.ownerId = :ownerId and a.status not in :excluded
            order by a.id asc, sc.changedAt asc, sc.id asc
            """)
    List<TimelineEntry> findTimelinesExcludingCurrentStatus(@Param("ownerId") Long ownerId,
                                                            @Param("excluded") Collection<ApplicationStatus> excluded);

    @Query("""
            select distinct t from JobApplication a join a.tags t
            where a.ownerId = :ownerId
            order by t
            """)
    List<String> findDistinctTags(@Param("ownerId") Long ownerId);
}
