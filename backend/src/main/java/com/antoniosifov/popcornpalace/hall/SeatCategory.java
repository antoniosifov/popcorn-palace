package com.antoniosifov.popcornpalace.hall;

/**
 * Pricing tier of a seat. Each showtime sets its own price per category.
 * The allowed values are also listed in the database CHECK constraints
 * (seats.category, showtime_prices.category); add a migration when adding one.
 */
public enum SeatCategory
{
    STANDARD,
    VIP
}
