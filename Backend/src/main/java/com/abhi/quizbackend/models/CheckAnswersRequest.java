package com.abhi.quizbackend.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The user's answers to one quiz. A question with no answer counts as wrong. */
public record CheckAnswersRequest(
        @NotNull Long quizId,
        @NotNull @Size(max = 100) List<@Valid @NotNull Answer> answers
) {

    /** @param chosenIndex the option index, or null when the time ran out */
    public record Answer(
            @NotNull Long questionId,
            @Min(0) @Max(3) Integer chosenIndex
    ) {
    }
}
