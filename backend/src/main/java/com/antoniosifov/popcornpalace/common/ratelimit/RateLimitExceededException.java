package com.antoniosifov.popcornpalace.common.ratelimit;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Too many requests in the current window; reported as 429 with a Retry-After header. */
public class RateLimitExceededException extends ApiException
{
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds)
    {
        super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS,
                "Too many attempts. Try again in %d seconds".formatted(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds()
    {
        return retryAfterSeconds;
    }
}
