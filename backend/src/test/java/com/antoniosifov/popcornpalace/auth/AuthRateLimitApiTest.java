package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.common.ratelimit.RateLimitExceededException;
import com.antoniosifov.popcornpalace.common.ratelimit.RateLimiter;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login and registration are throttled per client address. */
class AuthRateLimitApiTest extends ApiIntegrationTest
{
    /** The default of app.rate-limit.auth.max-requests. */
    private static final int LIMIT = 10;

    @DisplayName("After ten attempts in a minute, further logins from the same address get 429 with Retry-After")
    @Test
    void throttlesRepeatedLoginAttempts() throws Exception
    {
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            login("anna@example.com", "guess-" + attempt).andExpect(problem(401, "INVALID_CREDENTIALS"));
        }

        MvcResult blocked = login("anna@example.com", "guess-11")
                .andExpect(problem(429, "TOO_MANY_REQUESTS"))
                .andReturn();

        int retryAfter = Integer.parseInt(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isBetween(1, 60);
    }

    @DisplayName("Registration shares the same limit, so it cannot be used to keep guessing")
    @Test
    void registrationCountsAgainstTheSameLimit() throws Exception
    {
        for (int attempt = 1; attempt <= LIMIT; attempt++) {
            login("anna@example.com", "guess-" + attempt).andExpect(status().isUnauthorized());
        }

        anonymous.perform(post(AUTH + "/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "new@example.com", "password": "a-fine-password", "displayName": "New"}
                        """))
                .andExpect(problem(429, "TOO_MANY_REQUESTS"));
    }

    @DisplayName("When the window ends, attempts are allowed again")
    @Test
    void allowsAttemptsAgainAfterTheWindow() throws Exception
    {
        for (int attempt = 1; attempt <= LIMIT + 1; attempt++) {
            login("anna@example.com", "guess-" + attempt);
        }
        login("anna@example.com", "guess").andExpect(status().isTooManyRequests());

        // The counter is a Redis key that expires with the window; expire it now.
        assertThat(redis.keys("ratelimit:auth:*")).hasSize(1);
        redis.keys("ratelimit:auth:*").forEach(redis::delete);

        login("anna@example.com", "guess").andExpect(problem(401, "INVALID_CREDENTIALS"));
    }

    @DisplayName("The counter always has an expiry, so nobody stays blocked forever")
    @Test
    void counterExpires() throws Exception
    {
        login("anna@example.com", "guess");

        String key = redis.keys("ratelimit:auth:*").iterator().next();
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }

    @DisplayName("The limiter counts each key separately and reports when to retry")
    @Test
    void limiterCountsPerKey()
    {
        RateLimiter limiter = new RateLimiter(redis);

        limiter.check("test:a", 2, Duration.ofSeconds(30));
        limiter.check("test:a", 2, Duration.ofSeconds(30));
        limiter.check("test:b", 2, Duration.ofSeconds(30));

        assertThatThrownBy(() -> limiter.check("test:a", 2, Duration.ofSeconds(30)))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isBetween(1L, 30L));
        assertThatCode(() -> limiter.check("test:b", 2, Duration.ofSeconds(30))).doesNotThrowAnyException();
    }

    @DisplayName("If Redis is unreachable the limiter lets requests through instead of locking everyone out")
    @Test
    @SuppressWarnings("unchecked")
    void failsOpenWhenRedisIsDown()
    {
        StringRedisTemplate unreachable = mock(StringRedisTemplate.class);
        when(unreachable.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));

        assertThatCode(() -> new RateLimiter(unreachable).check("test:a", 1, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }

    private ResultActions login(String email, String password) throws Exception
    {
        return anonymous.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, password)));
    }
}
