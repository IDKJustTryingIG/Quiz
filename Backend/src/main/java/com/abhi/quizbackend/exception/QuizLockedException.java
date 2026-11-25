package com.abhi.quizbackend.exception;

/**
 * The quiz belongs to a contest that has not closed. Practice would show its
 * questions early. Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class QuizLockedException extends RuntimeException
{
    public QuizLockedException()
    {
        super("This quiz is part of today's contest. You can practise it after the contest closes.");
    }
}
