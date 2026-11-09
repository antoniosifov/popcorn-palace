package com.antoniosifov.popcornpalace.errors;

import org.springframework.http.HttpStatus;

/**
 * Error body returned by the API, for example
 * {@code {"statusCode": "BAD_REQUEST", "message": "..."}}.
 * The status is stored as its enum name so the JSON does not depend on
 * how the JSON library chooses to print enums.
 */
public record ApiError(String statusCode, String message)
{
    public ApiError(HttpStatus status, String message)
    {
        this(status.name(), message);
    }
}
