package com.abhi.quizbackend.exception;

/**
 * A topic has nothing the model can make questions from.
 * Mapped to HTTP 422 by {@link GlobalExceptionHandler}.
 */
public class QuizSourceException extends RuntimeException
{
    public QuizSourceException(String message)
    {
        super(message);
    }
}
