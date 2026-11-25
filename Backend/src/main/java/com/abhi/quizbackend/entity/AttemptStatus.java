package com.abhi.quizbackend.entity;

public enum AttemptStatus {
    /** The user is answering. */
    IN_PROGRESS,
    /** The user submitted before the time limit. */
    SUBMITTED,
    /** The time ran out. The server scored the answers the user had saved. */
    EXPIRED
}
