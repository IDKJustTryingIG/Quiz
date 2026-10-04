package com.abhi.quizbackend.models;

import com.abhi.quizbackend.entity.TrackVisibility;

import java.util.List;

/** A track with everything needed to show it: its days, topics and files. */
public record TrackDetailResponse(
        Long trackId,
        String name,
        String slug,
        String description,
        TrackVisibility visibility,
        Long ownerId,
        String ownerUsername,
        List<Day> days
) {

    /** position is 0-based; the UI shows it as "Day position + 1". */
    public record Day(
            Integer position,
            Long topicId,
            String topicName,
            List<FileResponse> files
    ) {
    }
}
