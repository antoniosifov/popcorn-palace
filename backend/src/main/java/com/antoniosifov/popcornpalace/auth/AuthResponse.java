package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.user.User;
import com.antoniosifov.popcornpalace.user.UserResponse;

/**
 * Returned by register and login. Send the token on later requests as
 * {@code Authorization: Bearer <accessToken>}.
 *
 * @param expiresIn seconds until the token expires
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user)
{
    public static AuthResponse of(TokenService.IssuedToken token, User user)
    {
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }
}
