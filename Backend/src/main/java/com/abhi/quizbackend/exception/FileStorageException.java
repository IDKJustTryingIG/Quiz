package com.abhi.quizbackend.exception;

/** Underlying file storage failed. Surfaces as a generic 500. */
public class FileStorageException extends RuntimeException
{
    public FileStorageException(String message)
    {
        super(message);
    }

    public FileStorageException(String message,Throwable cause)
    {
        super(message,cause);
    }
}
