package com.antoniosifov.popcornpalace.security;

import com.antoniosifov.popcornpalace.user.Role;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Who is making the request, read from the verified access token. Services
 * take this instead of a user id from the request body, so a client cannot
 * act on behalf of somebody else.
 */
public record CurrentUser(UUID id, Role role)
{
    /** Name of the token claim that carries the account's role. */
    public static final String ROLE_CLAIM = "role";

    public static CurrentUser from(Jwt jwt)
    {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString(ROLE_CLAIM)));
    }

    public boolean isAdmin()
    {
        return role == Role.ADMIN;
    }
}
