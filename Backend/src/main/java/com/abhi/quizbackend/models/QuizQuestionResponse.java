package com.abhi.quizbackend.models;

import java.util.List;

/**
 * One question as the generator returns it. The generator shuffles the
 * options and records where the correct one is, so the model never decides
 * that position.
 *
 * Server side only: it holds the answer. The client gets a
 * {@link QuizQuestionView}, and the answer only after it submits.
 */
public record QuizQuestionResponse(
        String question,
        List<String> options,
        int correctIndex,
        String explanation,
        String sourceFile
) {
}
