package com.abhi.quizbackend.models;

public record TrackTopicResponse(
        Long topicId,
        String name,
        String slug,
        String description,
        Integer position
) {
}
