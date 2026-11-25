package com.abhi.quizbackend.models;

import java.util.List;

/** The quiz for one topic (day) of a track, without its answers. */
public record QuizResponse(
        Long trackId,
        Long topicId,
        Long quizId,
        List<QuizQuestionView> questions
) {
}
