package com.antoniosifov.popcornpalace.common.error;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A business-rule failure that maps directly to an HTTP error response.
 * Services throw it; {@link ApiExceptionHandler} turns it into the response body.
 */
public class ApiException extends RuntimeException
{
    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, Object> details = new LinkedHashMap<>();

    public ApiException(HttpStatus status, ErrorCode code, String message)
    {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(ErrorCode code, String message)
    {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(ErrorCode code, String message)
    {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException notFound(ErrorCode code, String message)
    {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(ErrorCode code, String message)
    {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /**
     * Adds a field to the error response next to {@code code}, for data a client
     * can act on, such as which of the requested seats were already taken.
     */
    public ApiException with(String name, Object value)
    {
        details.put(name, value);
        return this;
    }

    public Map<String, Object> getDetails()
    {
        return Collections.unmodifiableMap(details);
    }

    public HttpStatus getStatus()
    {
        return status;
    }

    public ErrorCode getCode()
    {
        return code;
    }
}
