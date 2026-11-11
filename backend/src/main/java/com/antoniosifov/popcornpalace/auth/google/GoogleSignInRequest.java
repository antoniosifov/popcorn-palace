package com.antoniosifov.popcornpalace.auth.google;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of the request that signs in with a Google ID token. */
public record GoogleSignInRequest(
        @Schema(description = "The ID token Google issued for this application's client id")
        @NotBlank @Size(max = 4096)
        String idToken,

        @Schema(description = "The nonce that was sent to Google when the sign-in was started")
        @NotBlank @Size(max = 200)
        String nonce
)
{
    /** Keeps the token out of logs and error messages that print the request. */
    @Override
    public String toString()
    {
        return "GoogleSignInRequest[idToken=***, nonce=***]";
    }
}
