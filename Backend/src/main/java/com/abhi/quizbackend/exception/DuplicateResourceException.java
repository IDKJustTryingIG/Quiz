package com.abhi.quizbackend.exception;

import lombok.Getter;

/**
 * Thrown when a signup would collide with an account that already exists.
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
@Getter
public class DuplicateResourceException extends RuntimeException
{
    private final String field;

    public DuplicateResourceException(String field,String message)
    {
        super(message);
        this.field = field;
    }
}
