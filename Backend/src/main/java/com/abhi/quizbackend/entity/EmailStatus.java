package com.abhi.quizbackend.entity;

public enum EmailStatus {
    /** Waiting to be sent. After a temporary failure, waiting until next_attempt_at. */
    PENDING,
    SENT,
    /** The provider refused it, or every attempt failed. */
    FAILED,
    /** Not sent on purpose: the user turned the emails off, or it was too late to help. */
    SKIPPED
}
