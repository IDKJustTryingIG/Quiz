package com.abhi.quizbackend.exception;

import lombok.Getter;

/**
 * An uploaded file was rejected, e.g. a disallowed type.
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
@Getter
public class InvalidUploadException extends RuntimeException
{
    private final String field;

    public InvalidUploadException(String field,String message)
    {
        super(message);
        this.field = field;
    }
}
