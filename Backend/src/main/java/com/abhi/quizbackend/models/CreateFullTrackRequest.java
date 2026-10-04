package com.abhi.quizbackend.models;

import com.abhi.quizbackend.entity.TrackVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The JSON part of a multipart track upload. Topics are in day order: the
 * topic at index i is day i + 1, and its files arrive as the parts named
 * "files-{i}".
 */
public record CreateFullTrackRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotNull TrackVisibility visibility,
        @NotEmpty @Size(max = 365) List<@Valid TopicDraft> topics
) {

    public record TopicDraft(
            @NotBlank @Size(max = 255) String name
    ) {
    }
}
