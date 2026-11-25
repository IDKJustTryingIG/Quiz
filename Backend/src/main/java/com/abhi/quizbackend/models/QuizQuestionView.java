package com.abhi.quizbackend.models;

import java.util.List;

/**
 * One question as the client gets it before answering: no correct answer and
 * no explanation. The options keep their stored order, so an answer is an
 * index into this list.
 */
public record QuizQuestionView(
        Long questionId,
        String question,
        List<String> options,
        String sourceFile
) {
}
