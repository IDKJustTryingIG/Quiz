package com.abhi.quizbackend.models;

import com.abhi.quizbackend.entity.QuizJobStatus;

import java.time.Instant;
import java.time.LocalDate;

/** One job of the quiz queue, as the admin page shows it. */
public record QuizJobResponse(
        Long jobId,
        LocalDate runDate,
        Long trackId,
        String trackName,
        Long topicId,
        String topicName,
        QuizJobStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Long quizId,
        Instant finishedAt
) {
}
