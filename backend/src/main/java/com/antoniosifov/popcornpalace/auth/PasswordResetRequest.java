package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the request for a password reset link. */
public record PasswordResetRequest(
        @Schema(example = "anna@example.com")
        @NotBlank @Email @Size(max = 254)
        String email
)
{
    public PasswordResetRequest
    {
        email = User.normalizeEmail(email);
    }
}
