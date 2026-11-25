package com.abhi.quizbackend.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The ranking of one contest. It is final once the contest closes.
 *
 * A higher score ranks first. With equal scores, the shorter time ranks first.
 * Users still answering come last, with no rank.
 */
public record LeaderboardResponse(
        Long sessionId,
        String trackName,
        LocalDate sessionDate,
        boolean isFinal,
        Instant closesAt,
        Instant serverTime,
        List<Entry> entries
) {

    /** @param rank null while the user is still answering */
    public record Entry(
            Integer rank,
            String username,
            int dayNumber,
            String status,
            Integer score,
            int total,
            Long durationMs,
            boolean you
    ) {
    }
}
