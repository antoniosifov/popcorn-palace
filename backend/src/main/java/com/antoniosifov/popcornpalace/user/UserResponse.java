package com.antoniosifov.popcornpalace.user;

import java.time.Instant;
import java.util.UUID;

/** An account as shown to its owner. Never includes the password hash. */
public record UserResponse(UUID id, String email, String displayName, Role role, Instant createdAt)
{
    public static UserResponse from(User user)
    {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getCreatedAt());
    }
}
