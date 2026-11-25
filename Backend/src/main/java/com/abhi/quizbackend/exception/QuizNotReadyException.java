package com.abhi.quizbackend.exception;

/**
 * The day has no quiz yet. Opening a quiz never calls the model, so the quiz
 * must be prepared first. Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class QuizNotReadyException extends RuntimeException
{
    public QuizNotReadyException()
    {
        super("The quiz for this day is not ready yet.");
    }
}
