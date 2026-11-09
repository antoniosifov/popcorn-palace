package com.antoniosifov.popcornpalace.user;

/**
 * What an account may do. Customers browse and book; administrators also
 * manage movies, halls and showtimes. The allowed values are listed in the
 * database CHECK constraint ck_users_role as well.
 */
public enum Role
{
    CUSTOMER,
    ADMIN
}
