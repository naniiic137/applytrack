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

    /** Number of applications that ever reached one of the given statuses, based on the timeline. */
    @Query("""
            select count(distinct sc.application.id) from StatusChange sc
            where sc.application.ownerId = :ownerId and sc.toStatus in :statuses
            """)
    long countEverReached(@Param("ownerId") Long ownerId,
                          @Param("statuses") Collection<ApplicationStatus> statuses);

    @Query("""
            select distinct t from JobApplication a join a.tags t
            where a.ownerId = :ownerId
            order by t
            """)
    List<String> findDistinctTags(@Param("ownerId") Long ownerId);
}
