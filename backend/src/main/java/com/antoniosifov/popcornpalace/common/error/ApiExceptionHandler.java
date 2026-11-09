package com.antoniosifov.popcornpalace.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;

/**
 * Turns every failure into an RFC 9457 "problem details" response
 * ({@code application/problem+json}) with a machine-readable {@code code}:
 *
 * <pre>{@code
 * {
 *   "title": "Conflict",
 *   "status": 409,
 *   "detail": "Seat 15 is already booked for this showtime",
 *   "instance": "/api/v1/bookings",
 *   "code": "SEAT_ALREADY_BOOKED"
 * }
 * }</pre>
 *
 * The base class already handles Spring MVC's own exceptions (unknown path,
 * wrong method, type mismatch and so on) in the same format.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String CODE = "code";
    private static final String ERRORS = "errors";

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException exception)
    {
        return problem(exception.getStatus(), exception.getCode().name(), exception.getMessage());
    }

    /** A database constraint rejected the write: report the business rule it protects. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception)
    {
        return ConstraintErrors.resolve(exception)
                .map(error -> problem(HttpStatus.CONFLICT, error.code().name(), error.message()))
                .orElseGet(() -> {
                    log.warn("Unmapped data integrity violation", exception);
                    return problem(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT.name(),
                            "The request conflicts with existing data");
                });
    }

    /** Last resort: log the cause, but never leak internals to the client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception)
    {
        log.error("Unhandled exception", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred");
    }

    /** Bean Validation failed on a request body: list every rejected field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request)
    {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field).thenComparing(FieldViolation::message))
                .toList();

        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.name(),
                "Request validation failed");
        body.setProperty(ERRORS, violations);
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    /** The body is missing or is not valid JSON for the expected type. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request)
    {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST.name(),
                "Request body is missing or malformed");
        return handleExceptionInternal(exception, body, headers, status, request);
    }

    /**
     * Every response built by the base class passes through here. Framework-level
     * errors have no business code, so they get the HTTP status name instead
     * (for example {@code NOT_FOUND}); clients can then always rely on {@code code}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request)
    {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && !hasCode(problem)) {
            HttpStatus resolved = HttpStatus.resolve(status.value());
            problem.setProperty(CODE, resolved != null ? resolved.name() : "HTTP_" + status.value());
        }
        return response;
    }

    private static boolean hasCode(ProblemDetail problem)
    {
        return problem.getProperties() != null && problem.getProperties().containsKey(CODE);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail)
    {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }
}
