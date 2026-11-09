package com.antoniosifov.popcornpalace.booking;

/** The allowed values are also listed in the database CHECK constraint ck_bookings_status. */
public enum BookingStatus
{
    CONFIRMED,
    /** Cancelled by the customer or an administrator; its seats are on sale again. */
    CANCELLED
}
