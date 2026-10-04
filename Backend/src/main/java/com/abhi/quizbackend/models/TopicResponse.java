package com.abhi.quizbackend.models;

public record TopicResponse(
        Long topicId,
        String name,
        String slug,
        String description
) {
}
