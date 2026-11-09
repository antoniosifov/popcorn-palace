package com.antoniosifov.popcornpalace.common.text;

public final class Text
{
    private Text()
    {
    }

    /**
     * Trims the value and collapses runs of whitespace into single spaces, so
     * {@code "  The   Matrix "} and {@code "The Matrix"} are stored and compared
     * as the same text. Returns {@code null} for {@code null}.
     */
    public static String normalize(String value)
    {
        return value == null ? null : value.strip().replaceAll("\\s+", " ");
    }
}
