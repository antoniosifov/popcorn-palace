package com.antoniosifov.popcornpalace.showtime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long>
{
    // The entity graphs load each showtime together with its movie and hall in
    // one query, instead of extra queries per row when the response reads them.

    @Override
    @EntityGraph(attributePaths = {"movie", "hall"})
    Page<Showtime> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"movie", "hall"})
    Page<Showtime> findAllByMovieId(Long movieId, Pageable pageable);

    @EntityGraph(attributePaths = {"movie", "hall"})
    Optional<Showtime> findDetailedById(Long id);

    /**
     * Two time ranges overlap when each one starts before the other ends.
     * Back-to-back showtimes (one ends exactly when the next starts) do not overlap.
     * This query only produces a friendly error early; the guarantee is the
     * exclusion constraint ex_showtimes_hall_time.
     */
    @Query("""
            select count(s) > 0
            from Showtime s
            where s.hall.id = :hallId
              and s.startTime < :endTime
              and s.endTime > :startTime
            """)
    boolean existsOverlapping(@Param("hallId") Long hallId,
                              @Param("startTime") Instant startTime,
                              @Param("endTime") Instant endTime);

    /** Same check, ignoring the showtime being updated so it does not conflict with itself. */
    @Query("""
            select count(s) > 0
            from Showtime s
            where s.hall.id = :hallId
              and s.startTime < :endTime
              and s.endTime > :startTime
              and s.id <> :excludedId
            """)
    boolean existsOverlappingExcluding(@Param("hallId") Long hallId,
                                       @Param("startTime") Instant startTime,
                                       @Param("endTime") Instant endTime,
                                       @Param("excludedId") Long excludedId);
}
