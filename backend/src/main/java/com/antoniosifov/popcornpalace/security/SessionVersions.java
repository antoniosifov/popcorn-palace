package com.antoniosifov.popcornpalace.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Ends the sessions an account had before its password changed.
 *
 * <p>An access token is verified by its signature, with no table of sessions
 * to delete it from, so on its own it would outlive a password change by up
 * to its whole lifetime: exactly the hour in which a thief who knew the old
 * password is still logged in. Each token therefore carries the account's
 * session version, and a change of password raises it. The new version is
 * remembered here, in Redis, for as long as an older token could still be
 * unexpired; every request compares its token against it.
 *
 * <p>Two limits, on purpose. The memory lasts one token lifetime, because
 * after that every older token has expired by itself. And it is only as
 * durable as Redis: if Redis is restarted within that time, or cannot be
 * reached, tokens are judged by signature and expiry alone, as before. The
 * same choice as for rate limiting: an outage of Redis must not log everyone out.
 */
@Component
public class SessionVersions implements OAuth2TokenValidator<Jwt>
{
    /** Name of the token claim that carries the session version the token was issued for. */
    public static final String CLAIM = "sv";

    private static final Logger log = LoggerFactory.getLogger(SessionVersions.class);
    private static final OAuth2Error ENDED = new OAuth2Error("invalid_token",
            "The session was ended by a change of password", null);

    private final StringRedisTemplate redis;
    private final Duration tokenLifetime;

    public SessionVersions(StringRedisTemplate redis, SecurityProperties properties)
    {
        this.redis = redis;
        this.tokenLifetime = properties.jwt().ttl();
    }

    /**
     * Tokens of this account issued for an earlier version stop working.
     *
     * @throws DataAccessException if Redis cannot be reached: the caller is changing a
     *                             password and must not report success while old sessions live on
     */
    public void moveTo(UUID userId, int version)
    {
        redis.opsForValue().set(key(userId), String.valueOf(version), tokenLifetime);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token)
    {
        String current;
        try {
            current = redis.opsForValue().get(key(UUID.fromString(token.getSubject())));
        }
        catch (DataAccessException exception) {
            log.warn("Session versions are unavailable; accepting the token on its signature", exception);
            return OAuth2TokenValidatorResult.success();
        }
        catch (IllegalArgumentException exception) {
            // Not one of this application's tokens; the other checks say so.
            return OAuth2TokenValidatorResult.success();
        }
        if (current == null) {
            return OAuth2TokenValidatorResult.success();
        }
        Number issuedFor = token.getClaim(CLAIM);
        long version = issuedFor == null ? 0 : issuedFor.longValue();
        return version < Long.parseLong(current)
                ? OAuth2TokenValidatorResult.failure(ENDED)
                : OAuth2TokenValidatorResult.success();
    }

    private static String key(UUID userId)
    {
        return "auth:session-version:" + userId;
    }
}
