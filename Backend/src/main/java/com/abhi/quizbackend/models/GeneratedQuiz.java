package com.abhi.quizbackend.models;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * The exact shape the model must return. Spring AI turns these records into a
 * JSON schema and sends it to Gemini, so the descriptions are also instructions.
 * The top level wraps the list in an object, because some providers reject a
 * top-level array.
 */
public record GeneratedQuiz(
        @JsonPropertyDescription("The quiz questions")
        List<GeneratedQuestion> questions
) {

    public record GeneratedQuestion(
            @JsonPropertyDescription("The question text. Must be answerable from the attached file alone.")
            String question,

            @JsonPropertyDescription("The single correct answer.")
            String correctAnswer,

            @JsonPropertyDescription("Exactly 3 plausible but wrong answers, same type and similar length as the correct answer.")
            List<String> distractors,

            @JsonPropertyDescription("One or two sentences that explain why the correct answer is right, based on the file.")
            String explanation,

            @JsonPropertyDescription("The name of the file the question is based on, copied exactly from the file list.")
            String sourceFile
    ) {
    }
}
