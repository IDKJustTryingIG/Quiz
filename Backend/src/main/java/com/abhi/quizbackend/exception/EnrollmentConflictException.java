package com.abhi.quizbackend.exception;

/**
 * The enrollment cannot change the way the user asked, for example because
 * the user already follows another track. Mapped to HTTP 409 by
 * {@link GlobalExceptionHandler}.
 */
public class EnrollmentConflictException extends RuntimeException
{
    public EnrollmentConflictException(String message)
    {
        super(message);
    }
}
