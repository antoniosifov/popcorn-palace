package com.antoniosifov.popcornpalace.hall;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface SeatRepository extends JpaRepository<Seat, Long>
{
    List<Seat> findAllByHallIdOrderByRowNumberAscSeatNumberAsc(Long hallId);

    @Query("select distinct s.category from Seat s where s.hall.id = :hallId")
    Set<SeatCategory> findCategoriesByHallId(@Param("hallId") Long hallId);

    /** One line per row of the hall: its number, category and how many seats it has. */
    @Query("""
            select new com.antoniosifov.popcornpalace.hall.HallResponse$Row(s.rowNumber, s.category, count(s))
            from Seat s
            where s.hall.id = :hallId
            group by s.rowNumber, s.category
            order by s.rowNumber
            """)
    List<HallResponse.Row> findRowsByHallId(@Param("hallId") Long hallId);

    /**
     * Creates every seat of a block of identical rows with a single statement.
     * A hall can have hundreds of seats; inserting them one entity at a time
     * would mean one round trip to the database per seat.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO seats (hall_id, row_number, seat_number, category)
            SELECT CAST(:hallId AS BIGINT), r, n, CAST(:category AS VARCHAR)
            FROM generate_series(CAST(:firstRow AS INTEGER), CAST(:lastRow AS INTEGER)) AS r
                     CROSS JOIN generate_series(1, CAST(:seatsPerRow AS INTEGER)) AS n
            """)
    int insertRows(@Param("hallId") Long hallId,
                   @Param("firstRow") int firstRow,
                   @Param("lastRow") int lastRow,
                   @Param("seatsPerRow") int seatsPerRow,
                   @Param("category") String category);
}
