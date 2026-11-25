package com.abhi.quizbackend.exception;

/**
 * The contest cannot do what the user asked now, for example start after the
 * start window closed. Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ContestConflictException extends RuntimeException
{
    public ContestConflictException(String message)
    {
        super(message);
    }
}
