package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.Enrollment;
import com.abhi.quizbackend.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByUserUserIdAndStatus(Long userId, EnrollmentStatus status);

    long countByTrackTrackIdAndStatus(Long trackId, EnrollmentStatus status);

    Optional<Enrollment> findByUnsubscribeToken(String unsubscribeToken);

    /** Active enrollments that started on or before this date, with their user and track. */
    @EntityGraph(attributePaths = {"user", "track"})
    @Query("""
            select e from Enrollment e
            where e.status = com.abhi.quizbackend.entity.EnrollmentStatus.ACTIVE
              and e.startDate <= :date
            """)
    List<Enrollment> findActiveStartedBy(@Param("date") LocalDate date);

    /**
     * Each (track, start date) pair of the active enrollments that started on
     * or before this date. Users with the same pair are on the same day.
     */
    @Query("""
            select distinct new com.abhi.quizbackend.repository.TrackStart(e.track.trackId, e.startDate)
            from Enrollment e
            where e.status = com.abhi.quizbackend.entity.EnrollmentStatus.ACTIVE
              and e.startDate <= :date
            """)
    List<TrackStart> findActiveStarts(@Param("date") LocalDate date);
}
