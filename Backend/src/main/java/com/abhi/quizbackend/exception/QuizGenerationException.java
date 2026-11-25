package com.abhi.quizbackend.exception;

/**
 * The model did not produce a usable quiz. Mapped to HTTP 502 by
 * {@link GlobalExceptionHandler}: the request was valid, the upstream answer was not.
 * The message is shown to the user, so it must not contain internal detail.
 */
public class QuizGenerationException extends RuntimeException
{
    public QuizGenerationException(String message)
    {
        super(message);
    }
}
