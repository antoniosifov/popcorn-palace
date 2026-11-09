package com.antoniosifov.popcornpalace.common.error;

import org.springframework.http.HttpStatus;

/**
 * A business-rule failure that maps directly to an HTTP error response.
 * Services throw it; {@link ApiExceptionHandler} turns it into the response body.
 */
public class ApiException extends RuntimeException
{
    private final HttpStatus status;
    private final ErrorCode code;

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

    public static ApiException notFound(ErrorCode code, String message)
    {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(ErrorCode code, String message)
    {
        return new ApiException(HttpStatus.CONFLICT, code, message);
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
