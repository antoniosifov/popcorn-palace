package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.hall.Hall;
import com.antoniosifov.popcornpalace.hall.SeatCategory;
import com.antoniosifov.popcornpalace.movie.Movie;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Table(name = "showtimes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, not for application code
public class Showtime
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;

    /** Stored as an absolute point in time (timestamptz); clients convert to local time. */
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /**
     * Ticket price per seat category for this showtime (table showtime_prices).
     * Loaded in batches so listing a page of showtimes does not run one query each.
     */
    @ElementCollection
    @CollectionTable(name = "showtime_prices", joinColumns = @JoinColumn(name = "showtime_id"))
    @MapKeyColumn(name = "category", length = 20)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @BatchSize(size = 100)
    private Map<SeatCategory, BigDecimal> prices = new EnumMap<>(SeatCategory.class);

    public Showtime(Movie movie, Hall hall, Instant startTime, Instant endTime, Map<SeatCategory, BigDecimal> prices)
    {
        update(movie, hall, startTime, endTime, prices);
    }

    public void update(Movie movie, Hall hall, Instant startTime, Instant endTime, Map<SeatCategory, BigDecimal> prices)
    {
        this.movie = movie;
        this.hall = hall;
        this.startTime = startTime;
        this.endTime = endTime;
        // Change the existing map in place: Hibernate tracks this instance.
        this.prices.keySet().retainAll(prices.keySet());
        this.prices.putAll(prices);
    }

    public BigDecimal priceOf(SeatCategory category)
    {
        return prices.get(category);
    }
}
