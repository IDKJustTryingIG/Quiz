package com.abhi.quizbackend.entity;

public enum QuizJobStatus {
    /** Waiting for a worker. After a failure, waiting until next_attempt_at. */
    PENDING,
    /** A worker is asking the model now. */
    RUNNING,
    /** The topic has a quiz for the day. */
    DONE,
    /** The last attempt failed, and no more attempts are left. */
    FAILED
}
