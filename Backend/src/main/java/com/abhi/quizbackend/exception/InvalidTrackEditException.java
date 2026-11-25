package com.abhi.quizbackend.exception;

import lombok.Getter;

/**
 * A track edit refers to a day or file that is not part of the track.
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
@Getter
public class InvalidTrackEditException extends RuntimeException
{
    private final String field;

    public InvalidTrackEditException(String field,String message)
    {
        super(message);
        this.field = field;
    }
}
