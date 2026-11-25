package com.abhi.quizbackend.models;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One answer of an attempt. The first answer to a question is final. */
public record AnswerRequest(
        @NotNull Long questionId,
        @NotNull @Min(0) @Max(3) Integer chosenIndex
) {
}
