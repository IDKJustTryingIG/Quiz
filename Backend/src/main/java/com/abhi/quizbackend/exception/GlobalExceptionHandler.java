package com.abhi.quizbackend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own exceptions
 * (unknown path, wrong method, unsupported media type, malformed JSON) keep their
 * correct 4xx status. Without it the catch-all below would report every one as a 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request)
    {
        Map<String,String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors())
        {
            // Keep the first message per field so the response stays predictable.
            fieldErrors.putIfAbsent(fieldError.getField(),fieldError.getDefaultMessage());
        }

        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed",
                pathOf(request),
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Gives every exception handled by the base class the same JSON shape, instead of
     * the empty body Spring returns by default.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request)
    {
        if (body == null)
        {
            HttpStatus status = HttpStatus.valueOf(statusCode.value());
            body = ApiError.of(status.value(),status.getReasonPhrase(),status.getReasonPhrase(),pathOf(request));
        }

        return super.handleExceptionInternal(ex,body,headers,statusCode,request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException ex,HttpServletRequest request)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                Map.of(ex.getField(),ex.getMessage())));
    }

    @ExceptionHandler(EnrollmentConflictException.class)
    public ResponseEntity<ApiError> handleEnrollmentConflict(EnrollmentConflictException ex,HttpServletRequest request)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler({ContestConflictException.class, QuizLockedException.class})
    public ResponseEntity<ApiError> handleContestConflict(RuntimeException ex,HttpServletRequest request)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(InvalidUploadException.class)
    public ResponseEntity<ApiError> handleInvalidUpload(InvalidUploadException ex,HttpServletRequest request)
    {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                Map.of(ex.getField(),ex.getMessage())));
    }

    @ExceptionHandler(QuizSourceException.class)
    public ResponseEntity<ApiError> handleQuizSource(QuizSourceException ex,HttpServletRequest request)
    {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiError.of(
                HttpStatus.UNPROCESSABLE_CONTENT.value(),
                HttpStatus.UNPROCESSABLE_CONTENT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(QuizNotReadyException.class)
    public ResponseEntity<ApiError> handleQuizNotReady(QuizNotReadyException ex,HttpServletRequest request)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(QuizGenerationException.class)
    public ResponseEntity<ApiError> handleQuizGeneration(QuizGenerationException ex,HttpServletRequest request)
    {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError.of(
                HttpStatus.BAD_GATEWAY.value(),
                HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(InvalidTrackEditException.class)
    public ResponseEntity<ApiError> handleInvalidTrackEdit(InvalidTrackEditException ex,HttpServletRequest request)
    {
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                Map.of(ex.getField(),ex.getMessage())));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex,HttpServletRequest request)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,HttpServletRequest request)
    {
        // Never echo ex.getMessage(): it distinguishes "no such user" from "wrong password".
        log.warn("Failed authentication on {}: {}",request.getRequestURI(),ex.getClass().getSimpleName());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Invalid email or password",
                request.getRequestURI()));
    }

    /**
     * Missing, unknown, expired or replayed refresh token. The client's only correct
     * response is to send the user back through login, so the message says so plainly.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex,
                                                              HttpServletRequest request)
    {
        log.warn("Refresh rejected on {}: {}",request.getRequestURI(),ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Session expired. Please sign in again.",
                request.getRequestURI()));
    }

    /**
     * Without this, the catch-all below would turn every authorization failure into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,HttpServletRequest request)
    {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "You do not have permission to perform this action",
                request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex,HttpServletRequest request)
    {
        // Log the detail, return a generic message: internals must not leak to clients.
        log.error("Unhandled exception on {} {}",request.getMethod(),request.getRequestURI(),ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Something went wrong. Please try again later.",
                request.getRequestURI()));
    }

    private String pathOf(WebRequest request)
    {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false);
    }
}
