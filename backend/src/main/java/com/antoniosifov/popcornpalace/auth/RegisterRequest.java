package com.antoniosifov.popcornpalace.auth;

import com.antoniosifov.popcornpalace.common.text.Text;
import com.antoniosifov.popcornpalace.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the register request. There is no role field: every registration creates a customer. */
public record RegisterRequest(
        @Schema(example = "anna@example.com")
        @NotBlank @Email @Size(max = 254)
        String email,

        @Schema(example = "correct-horse-battery", description = "8 to 72 characters")
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Schema(example = "Anna")
        @NotBlank @Size(max = 100)
        String displayName
)
{
    public RegisterRequest
    {
        email = User.normalizeEmail(email);
        displayName = Text.normalize(displayName);
    }

    /** Keeps the password out of logs and error messages that print the request. */
    @Override
    public String toString()
    {
        return "RegisterRequest[email=%s, displayName=%s, password=***]".formatted(email, displayName);
    }
}
