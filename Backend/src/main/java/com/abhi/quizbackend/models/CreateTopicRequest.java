package com.abhi.quizbackend.models;

import jakarta.validation.constraints.NotBlank;

public record CreateTopicRequest(
        @NotBlank String name,
        @NotBlank String slug,
        String description
) {
}
