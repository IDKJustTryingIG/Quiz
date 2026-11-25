package com.abhi.quizbackend.models;

import java.time.Instant;
import java.util.List;

/**
 * The score of an attempt. The correct answers stay hidden until the contest
 * closes (revealAt), so nobody can pass them to users still answering.
 *
 * @param results empty until revealed
 */
public record AttemptResultResponse(
        Long attemptId,
        String status,
        Integer score,
        int total,
        Long durationMs,
        boolean revealed,
        Instant revealAt,
        List<QuizResultResponse.QuestionResult> results
) {
}
