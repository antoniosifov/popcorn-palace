package com.antoniosifov.popcornpalace.common.error;

import com.antoniosifov.popcornpalace.common.ratelimit.RateLimitExceededException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
 *   "code": "SEAT_ALREADY_BOOKED",
 *   "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
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
    private static final String TRACE_ID = "traceId";

    private final Tracer tracer;

    public ApiExceptionHandler(Tracer tracer)
    {
        this.tracer = tracer;
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException exception)
    {
        ProblemDetail problem = problem(exception.getStatus(), exception.getCode().name(), exception.getMessage());
        exception.getDetails().forEach(problem::setProperty);
        return problem;
    }

    /** Tells the client when it may try again, in the standard Retry-After header. */
    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException exception)
    {
        return ResponseEntity.status(exception.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(handleApiException(exception));
    }

    /**
     * The request carries no usable credentials: no token, or one that is
     * malformed, wrongly signed or expired. Spring Security raises this in its
     * filter chain and hands it over through ProblemSecurityHandlers.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception)
    {
        String detail = exception instanceof InvalidBearerTokenException
                ? "The access token is invalid or has expired"
                : "Authentication is required to access this resource";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.name(), detail));
    }

    /** The caller is known, but the account's role does not allow this action. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception)
    {
        return problem(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.name(), "You do not have permission to perform this action");
    }

    /**
     * The database rejected the write. If one of our named constraints did it,
     * report the business rule it protects; otherwise it is either an unmapped
     * integrity violation (still a conflict) or a real database failure.
     */
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDataAccess(DataAccessException exception)
    {
        Optional<ConstraintErrors.ConstraintError> known = ConstraintErrors.resolve(exception);
        if (known.isPresent()) {
            return problem(HttpStatus.CONFLICT, known.get().code().name(), known.get().message());
        }
        if (exception instanceof DataIntegrityViolationException) {
            log.warn("Unmapped data integrity violation", exception);
            return problem(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT.name(), "The request conflicts with existing data");
        }
        if (exception instanceof ConcurrencyFailureException) {
            // Two requests changed the same row at once (optimistic lock, or a
            // lock that could not be taken). The loser did nothing and may retry.
            return problem(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_UPDATE.name(),
                    "The resource was changed by another request at the same time. Please retry");
        }
        if (exception instanceof DataAccessResourceFailureException || exception instanceof QueryTimeoutException) {
            // PostgreSQL or Redis cannot be reached or did not answer in time:
            // not the client's fault and worth retrying, hence 503 rather than 500.
            log.error("A backing service is unavailable", exception);
            return problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.name(),
                    "The service is temporarily unavailable. Please try again shortly");
        }
        return handleUnexpected(exception);
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
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (!hasCode(problem)) {
                HttpStatus resolved = HttpStatus.resolve(status.value());
                problem.setProperty(CODE, resolved != null ? resolved.name() : "HTTP_" + status.value());
            }
            withTraceId(problem);
        }
        return response;
    }

    private static boolean hasCode(ProblemDetail problem)
    {
        return problem.getProperties() != null && problem.getProperties().containsKey(CODE);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail)
    {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return withTraceId(problem);
    }

    /**
     * Names the trace of the request, the same id the {@code X-Trace-Id} header
     * carries, so a reported error can be matched to its logs and spans.
     */
    private ProblemDetail withTraceId(ProblemDetail problem)
    {
        Span span = tracer.currentSpan();
        if (span != null) {
            problem.setProperty(TRACE_ID, span.context().traceId());
        }
        return problem;
    }
}
