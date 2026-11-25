package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {

    Optional<QuizSession> findByTrackTrackIdAndSessionDate(Long trackId, LocalDate sessionDate);

    List<QuizSession> findBySessionDate(LocalDate sessionDate);

    /** Closed contests whose unsubmitted attempts are not scored yet. */
    List<QuizSession> findByClosesAtLessThanEqualAndScoredAtIsNull(Instant now);
}
