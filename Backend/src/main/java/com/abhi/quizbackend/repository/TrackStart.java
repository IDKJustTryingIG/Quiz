package com.abhi.quizbackend.repository;

import java.time.LocalDate;

/** A track and a start date that at least one active enrollment has. */
public record TrackStart(Long trackId, LocalDate startDate) {
}
