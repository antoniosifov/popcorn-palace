package com.antoniosifov.popcornpalace.hall;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A screening room. Its seat layout is fixed when the hall is created:
 * bookings point at individual seats, so reshaping a hall in place would
 * silently change what past customers bought.
 */
@Entity
@Table(name = "halls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class Hall
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "row_count", nullable = false, updatable = false)
    private int rowCount;

    @Column(name = "seat_count", nullable = false, updatable = false)
    private int seatCount;

    public Hall(String name, int rowCount, int seatCount)
    {
        this.name = name;
        this.rowCount = rowCount;
        this.seatCount = seatCount;
    }
}
