package com.abhi.quizbackend.models;

import java.time.LocalDate;

/**
 * The user's active enrollment, with where they are in the track today.
 *
 * @param dayNumber today's day, from 1. It is 0 before the start date, and
 *                  greater than totalDays after the last day.
 * @param today     today's topic, or null when the track has not started or is finished
 * @param emailOptIn whether the user gets the 06:00 email
 */
public record EnrollmentResponse(
        Long enrollmentId,
        Long trackId,
        String trackName,
        String trackSlug,
        LocalDate startDate,
        int dayNumber,
        int totalDays,
        Progress progress,
        Today today,
        boolean emailOptIn
) {

    public enum Progress {
        /** Day 1 is a later date. */
        NOT_STARTED,
        IN_PROGRESS,
        /** The user went past the last day. */
        FINISHED
    }

    public record Today(Long topicId, String topicName) {
    }
}
