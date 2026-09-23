package dev.hamza.applytrack.application;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {

    @Query("""
            select i from Interview i join fetch i.application a
            where a.ownerId = :ownerId and i.scheduledAt >= :from
            order by i.scheduledAt asc
            """)
    List<Interview> findUpcoming(@Param("ownerId") Long ownerId, @Param("from") Instant from, Limit limit);
}
