package com.abhi.quizbackend.models;

import com.abhi.quizbackend.entity.TrackVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTrackRequest(
        @NotBlank String name,
        @NotBlank String slug,
        String description,
        @NotNull TrackVisibility visibility
) {
}
