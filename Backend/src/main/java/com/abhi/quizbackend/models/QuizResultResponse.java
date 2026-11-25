package com.abhi.quizbackend.models;

import java.util.List;

/** The score of a submitted quiz, with the answer to every question. */
public record QuizResultResponse(
        Long quizId,
        int score,
        int total,
        List<QuestionResult> results
) {

    /** @param chosenIndex the user's option, or null if they gave none */
    public record QuestionResult(
            Long questionId,
            Integer chosenIndex,
            int correctIndex,
            boolean correct,
            String explanation
    ) {
    }
}
