package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.QuizGenerationJob;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface QuizGenerationJobRepository extends JpaRepository<QuizGenerationJob, Long> {

    List<QuizGenerationJob> findByRunDate(LocalDate runDate);

    /** The day's jobs with their track and topic, for the admin page. */
    @EntityGraph(attributePaths = {"track", "topic"})
    List<QuizGenerationJob> findByRunDateOrderByJobIdAsc(LocalDate runDate);

    /**
     * The next PENDING jobs whose delay is over, locked for this transaction.
     *
     * The lock timeout -2 is SKIP LOCKED: a job another worker holds is
     * skipped, not waited for, so two workers never take the same job.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select j from QuizGenerationJob j
            where j.status = com.abhi.quizbackend.entity.QuizJobStatus.PENDING
              and j.nextAttemptAt <= :now
            order by j.nextAttemptAt, j.jobId
            """)
    List<QuizGenerationJob> findClaimable(@Param("now") Instant now, Pageable limit);

    @Query("""
            select count(j) > 0 from QuizGenerationJob j
            where j.status = com.abhi.quizbackend.entity.QuizJobStatus.PENDING
              and j.nextAttemptAt <= :now
            """)
    boolean existsClaimable(@Param("now") Instant now);

    /**
     * Puts RUNNING jobs whose worker stopped (for example, the server
     * restarted) back in the queue.
     */
    @Modifying
    @Query("""
            update QuizGenerationJob j
            set j.status = com.abhi.quizbackend.entity.QuizJobStatus.PENDING, j.lockedAt = null
            where j.status = com.abhi.quizbackend.entity.QuizJobStatus.RUNNING
              and j.lockedAt < :cutoff
            """)
    int releaseStale(@Param("cutoff") Instant cutoff);
}
