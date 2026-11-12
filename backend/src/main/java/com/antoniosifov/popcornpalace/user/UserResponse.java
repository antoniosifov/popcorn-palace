package com.antoniosifov.popcornpalace.user;

import java.time.Instant;
import java.util.UUID;

/**
 * An account as shown to its owner. Never includes the password hash.
 *
 * @param hasPassword false for an account that is only ever entered through Google
 */
public record UserResponse(UUID id, String email, String displayName, Role role, Instant createdAt, boolean hasPassword)
{
    public static UserResponse from(User user)
    {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getCreatedAt(), user.hasPassword());
    }
}
