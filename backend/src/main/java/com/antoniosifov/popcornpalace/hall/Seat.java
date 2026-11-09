package com.antoniosifov.popcornpalace.hall;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One physical seat in a hall. Seats are created in bulk with the hall
 * (see {@link SeatRepository#insertRows}) and never change afterwards, so the
 * application only reads this entity.
 */
@Entity
@Table(name = "seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class Seat
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false, updatable = false)
    private Hall hall;

    /** Rows are numbered from the screen, starting at 1. */
    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    /** Position within the row, starting at 1. */
    @Column(name = "seat_number", nullable = false, updatable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private SeatCategory category;
}
