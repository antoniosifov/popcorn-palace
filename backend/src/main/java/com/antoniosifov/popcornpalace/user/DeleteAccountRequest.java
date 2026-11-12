package com.antoniosifov.popcornpalace.user;

import jakarta.validation.constraints.Size;

/**
 * @param password the account's password; left out for an account that has none
 */
public record DeleteAccountRequest(
        @Size(max = 100, message = "must be at most 100 characters long")
        String password
)
{
    /** Keeps the password out of logs and error messages that print the request. */
    @Override
    public String toString()
    {
        return "DeleteAccountRequest[password=***]";
    }
}
