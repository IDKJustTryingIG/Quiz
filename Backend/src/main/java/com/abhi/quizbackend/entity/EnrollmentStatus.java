package com.abhi.quizbackend.entity;

public enum EnrollmentStatus {
    /** The user follows the track. A user has at most one active enrollment. */
    ACTIVE,
    /** The user went past the last day of the track. */
    COMPLETED,
    /** The user left the track, or switched to another one. */
    LEFT
}
