package com.antoniosifov.popcornpalace.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Settings under {@code app.security} in application.yaml. */
@ConfigurationProperties("app.security")
public record SecurityProperties(Jwt jwt, Admin admin)
{
    /**
     * @param secret HS256 signing key, at least 32 bytes; empty means "generate a random one at startup"
     * @param ttl    how long an access token stays valid
     * @param issuer value of the {@code iss} claim, checked on every request
     */
    public record Jwt(String secret, Duration ttl, String issuer)
    {
    }

    /** Optional administrator account created at startup; see {@code AdminBootstrap}. */
    public record Admin(String email, String password)
    {
    }
}
