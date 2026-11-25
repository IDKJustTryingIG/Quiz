package com.abhi.quizbackend.models;

import java.time.Instant;
import java.util.List;

/**
 * An attempt in progress: the questions, without answers, and what the user
 * answered so far. A reload of the page resumes from here.
 */
public record AttemptResponse(
        Long attemptId,
        Long sessionId,
        String status,
        Instant startedAt,
        Instant deadlineAt,
        Instant serverTime,
        List<QuizQuestionView> questions,
        List<SavedAnswer> answers
) {

    public record SavedAnswer(Long questionId, int chosenIndex) {
    }
}
