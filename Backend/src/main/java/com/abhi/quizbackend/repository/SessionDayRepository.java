package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.SessionDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;

public interface SessionDayRepository extends JpaRepository<SessionDay, Long> {

    /**
     * Pins a new quiz to the day groups of this topic on this date. A day
     * group of an open contest keeps its quiz; it gets one only if it has
     * none yet (a late quiz, made after 18:00).
     */
    @Modifying
    @Query("""
            update SessionDay d set d.quizId = :quizId
            where d.topic.topicId = :topicId
              and d.session.sessionId in (
                  select s.sessionId from QuizSession s
                  where s.sessionDate = :date
                    and (s.opensAt > :now or d.quizId is null))
            """)
    int pin(@Param("topicId") Long topicId,
            @Param("date") LocalDate date,
            @Param("quizId") Long quizId,
            @Param("now") Instant now);

    /** Whether a contest that has not closed yet uses this quiz. */
    @Query("""
            select count(d) > 0 from SessionDay d
            where d.quizId = :quizId and d.session.closesAt > :now
            """)
    boolean isInUnclosedContest(@Param("quizId") Long quizId, @Param("now") Instant now);
}
