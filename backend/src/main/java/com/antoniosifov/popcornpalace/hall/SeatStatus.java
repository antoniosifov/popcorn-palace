package com.antoniosifov.popcornpalace.hall;

/** What a seat is, for one showtime, from a customer's point of view. */
public enum SeatStatus
{
    /** Free to hold. */
    AVAILABLE,
    /** Temporarily reserved by a customer who is checking out; becomes available again if they do not finish. */
    HELD,
    /** Sold. */
    BOOKED
}
