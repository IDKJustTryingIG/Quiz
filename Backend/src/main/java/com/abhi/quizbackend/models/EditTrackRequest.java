package com.abhi.quizbackend.models;

import com.abhi.quizbackend.entity.TrackVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The JSON part of a multipart track edit. It describes the whole track after
 * the edit. The track name is not here: the owner cannot change it.
 *
 * The day at index i becomes day i + 1. Its new files arrive in the parts
 * named "files-{i}". A day that is not in the list is removed.
 */
public record EditTrackRequest(
        @Size(max = 2000) String description,
        @NotNull TrackVisibility visibility,
        @NotEmpty @Size(max = 365) List<@Valid DayEdit> days
) {

    /**
     * @param topicId     the topic of an existing day, or null for a new day
     * @param name        the topic name
     * @param keepFileIds the existing files to keep; files not listed are removed
     */
    public record DayEdit(
            Long topicId,
            @NotBlank @Size(max = 255) String name,
            List<Long> keepFileIds
    ) {
    }
}
