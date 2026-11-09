package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Body of the login request. */
public record LoginRequest(
        @Schema(example = "anna@example.com")
        @NotBlank
        String email,

        @Schema(example = "correct-horse-battery")
        @NotBlank
        String password
)
{
    public LoginRequest
    {
        email = User.normalizeEmail(email);
    }

    /** Keeps the password out of logs and error messages that print the request. */
    @Override
    public String toString()
    {
        return "LoginRequest[email=%s, password=***]".formatted(email);
    }
}
