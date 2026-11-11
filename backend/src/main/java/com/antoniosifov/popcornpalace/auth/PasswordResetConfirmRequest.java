package com.antoniosifov.popcornpalace.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the request that sets a new password with the token from the email. */
public record PasswordResetConfirmRequest(
        @Schema(description = "The token from the link in the email")
        @NotBlank @Size(max = 200)
        String token,

        @Schema(example = "a-new-horse-battery", description = "8 to 72 characters")
        @NotBlank @Size(min = 8, max = 72)
        String password
)
{
    /** Keeps the token and the password out of logs and error messages that print the request. */
    @Override
    public String toString()
    {
        return "PasswordResetConfirmRequest[token=***, password=***]";
    }
}
