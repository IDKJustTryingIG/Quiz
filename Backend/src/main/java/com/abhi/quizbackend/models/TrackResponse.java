package com.abhi.quizbackend.models;

import com.abhi.quizbackend.entity.TrackVisibility;

public record TrackResponse(
        Long trackId,
        String name,
        String slug,
        String description,
        TrackVisibility visibility,
        Long ownerId,
        String ownerUsername,
        Integer topicCount
) {
}
