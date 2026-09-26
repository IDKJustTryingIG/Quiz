package com.abhi.quizbackend.exception;

/** Refresh token missing, unknown, expired, or already used. Always surfaces as 401. */
public class InvalidRefreshTokenException extends RuntimeException
{
    public InvalidRefreshTokenException(String message)
    {
        super(message);
    }
}
