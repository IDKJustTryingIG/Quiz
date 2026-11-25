package com.abhi.quizbackend.models;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Today's contest of the user's track, from the user's side.
 *
 * serverTime lets the browser correct its own clock for the countdown.
 * The fields after state are null when state is NONE.
 */
public record ContestTodayResponse(
        State state,
        Instant serverTime,
        Long sessionId,
        Long trackId,
        String trackName,
        String trackSlug,
        LocalDate sessionDate,
        Integer dayNumber,
        Integer totalDays,
        String topicName,
        Instant opensAt,
        Instant startDeadline,
        Instant closesAt,
        boolean quizReady,
        boolean canStart,
        AttemptSummary attempt
) {

    public enum State {
        /** The user follows no track, has not started it, or finished it. */
        NONE,
        /** The user has a day today, but the server has not planned the contest yet. */
        NOT_PLANNED,
        UPCOMING,
        OPEN,
        CLOSED
    }

    /** The user's attempt. score is null until scored. */
    public record AttemptSummary(
            Long attemptId,
            String status,
            Instant deadlineAt,
            Integer score,
            int total,
            Long durationMs
    ) {
    }

    public static ContestTodayResponse none(Instant serverTime) {
        return new ContestTodayResponse(State.NONE, serverTime, null, null, null, null, null, null, null,
                null, null, null, null, false, false, null);
    }
}
