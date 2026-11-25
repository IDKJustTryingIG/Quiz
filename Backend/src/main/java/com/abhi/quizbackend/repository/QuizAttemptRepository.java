package com.abhi.quizbackend.repository;

import com.abhi.quizbackend.entity.AttemptStatus;
import com.abhi.quizbackend.entity.QuizAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    Optional<QuizAttempt> findBySessionSessionIdAndUserUserId(Long sessionId, Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<QuizAttempt> findBySessionSessionId(Long sessionId);

    List<QuizAttempt> findBySessionSessionIdAndStatus(Long sessionId, AttemptStatus status);
}
