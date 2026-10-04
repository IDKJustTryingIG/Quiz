package com.abhi.quizbackend.models;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddTopicToTrackRequest(
        @NotNull @Min(0) Integer position
) {
}
