package com.antoniosifov.popcornpalace.notifications;

/**
 * The message cannot be understood: it is not valid JSON or lacks required
 * fields. Retrying will not help, so such a message goes straight to the
 * dead-letter topic instead of being retried.
 */
public class InvalidEventException extends RuntimeException
{
    public InvalidEventException(String message)
    {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
