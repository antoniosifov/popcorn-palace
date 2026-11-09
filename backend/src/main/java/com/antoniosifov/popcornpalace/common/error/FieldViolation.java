package com.antoniosifov.popcornpalace.common.error;

/** One rejected request field, listed under {@code errors} in a validation error response. */
public record FieldViolation(String field, String message)
{
}
