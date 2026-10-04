package com.abhi.quizbackend.models;

import java.util.List;

/** One page of tracks, plus what the client needs to draw the pager. */
public record TrackPageResponse(
        List<TrackResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
}
