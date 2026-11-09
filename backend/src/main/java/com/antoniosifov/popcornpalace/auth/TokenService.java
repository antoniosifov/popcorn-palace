package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.security.CurrentUser;
import com.antoniosifov.popcornpalace.security.SecurityProperties;
import com.antoniosifov.popcornpalace.user.Role;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Issues signed access tokens. Verification is configured in the security package. */
@Service
public class TokenService
{
    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds)
    {
    }

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;

    public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock)
    {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = properties.jwt().issuer();
        this.ttl = properties.jwt().ttl();
    }

    /**
     * The token carries only what is needed to authorize a request: who the
     * account is (subject) and its role. No email or name, so nothing personal
     * is exposed if a token leaks into a log.
     */
    public IssuedToken issue(UUID userId, Role role)
    {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(CurrentUser.ROLE_CLAIM, role.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt, ttl.toSeconds());
    }
}
