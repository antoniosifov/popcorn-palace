package com.antoniosifov.popcornpalace.demo;

import com.antoniosifov.popcornpalace.hall.HallRepository;
import com.antoniosifov.popcornpalace.hall.HallService;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.movie.MovieRepository;
import com.antoniosifov.popcornpalace.movie.MovieService;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import com.antoniosifov.popcornpalace.showtime.ShowtimeService;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo programme against the real database. The programme is built by
 * hand with a fixed clock, as it is switched off in the test configuration.
 */
class DemoProgrammeTest extends ApiIntegrationTest
{
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    /** A Wednesday, 09:00 in Berlin (UTC+1 in January): before the first showtime of the day. */
    private static final Instant MORNING = Instant.parse("2030-01-16T08:00:00Z");

    @Autowired
    private ApplicationContext context;
    @Autowired
    private MovieService movieService;
    @Autowired
    private HallService hallService;
    @Autowired
    private ShowtimeService showtimeService;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private HallRepository hallRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private ShowtimeRepository showtimeRepository;

    @DisplayName("It is off unless asked for")
    @Test
    void isOffByDefault()
    {
        assertThat(context.getBeansOfType(DemoProgramme.class)).isEmpty();
    }

    @DisplayName("An empty installation gets halls, movies and a week of showtimes on the cinema's clock")
    @Test
    void fillsAnEmptyInstallation() throws Exception
    {
        int added = programmeAt(MORNING, 7).topUp();

        assertThat(hallRepository.count()).isEqualTo(3);
        assertThat(movieRepository.count()).isEqualTo(8);
        // 3 halls x 4 showtimes a day x 7 days.
        assertThat(added).isEqualTo(84);
        assertThat(showtimeRepository.count()).isEqualTo(84);

        // The first showtime starts at 11:00 in Berlin, which is 10:00 UTC, at the matinee price.
        mvc.perform(get(SHOWTIMES).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].startTime").value("2030-01-16T10:00:00Z"))
                .andExpect(jsonPath("$.content[0].prices.STANDARD").value(11.0));

        // Every movie of the catalog is on.
        Integer moviesShown = jdbc.queryForObject("SELECT count(DISTINCT movie_id) FROM showtimes", Integer.class);
        assertThat(moviesShown).isEqualTo(8);
    }

    @DisplayName("A showtime is priced for the kinds of seat its hall has, and for no other")
    @Test
    void pricesOnlyTheSeatCategoriesOfTheHall()
    {
        programmeAt(MORNING, 1).topUp();

        // The Screening Room has standard seats only; the other two halls have VIP rows as well.
        Integer vipPricesInTheScreeningRoom = jdbc.queryForObject("""
                SELECT count(*) FROM showtime_prices p
                  JOIN showtimes s ON s.id = p.showtime_id
                  JOIN halls h ON h.id = s.hall_id
                 WHERE h.name = 'Screening Room' AND p.category = 'VIP'
                """, Integer.class);
        Integer vipPricesElsewhere = jdbc.queryForObject("""
                SELECT count(*) FROM showtime_prices p
                  JOIN showtimes s ON s.id = p.showtime_id
                  JOIN halls h ON h.id = s.hall_id
                 WHERE h.name <> 'Screening Room' AND p.category = 'VIP'
                """, Integer.class);

        assertThat(vipPricesInTheScreeningRoom).isZero();
        assertThat(vipPricesElsewhere).isEqualTo(8);
    }

    @DisplayName("No two halls show the same movie at the same time")
    @Test
    void hallsShowDifferentMoviesAtTheSameTime()
    {
        programmeAt(MORNING, 7).topUp();

        Integer clashes = jdbc.queryForObject("""
                SELECT count(*) FROM showtimes a JOIN showtimes b
                  ON a.start_time = b.start_time AND a.movie_id = b.movie_id AND a.hall_id < b.hall_id
                """, Integer.class);
        assertThat(clashes).isZero();
    }

    @DisplayName("Running it again adds nothing")
    @Test
    void isRepeatable()
    {
        DemoProgramme programme = programmeAt(MORNING, 7);
        programme.topUp();

        assertThat(programme.topUp()).isZero();
        assertThat(showtimeRepository.count()).isEqualTo(84);
        assertThat(movieRepository.count()).isEqualTo(8);
    }

    @DisplayName("A day later, exactly one more day is scheduled")
    @Test
    void rollsForwardOneDayAtATime()
    {
        programmeAt(MORNING, 7).topUp();

        int added = programmeAt(MORNING.plusSeconds(24 * 3600), 7).topUp();

        assertThat(added).isEqualTo(12);
        Instant lastStart = jdbc.queryForObject("SELECT max(start_time) FROM showtimes", java.sql.Timestamp.class).toInstant();
        // 20:45 in Berlin on the 23rd.
        assertThat(lastStart).isEqualTo(Instant.parse("2030-01-23T19:45:00Z"));
    }

    @DisplayName("Showtimes that would already have started are not scheduled")
    @Test
    void skipsWhatHasAlreadyStarted()
    {
        // 15:00 in Berlin: the 11:00 and 14:15 showtimes of today are gone.
        int added = programmeAt(Instant.parse("2030-01-16T14:00:00Z"), 1).topUp();

        assertThat(added).isEqualTo(6);
        Instant firstStart = jdbc.queryForObject("SELECT min(start_time) FROM showtimes", java.sql.Timestamp.class).toInstant();
        assertThat(firstStart).isEqualTo(Instant.parse("2030-01-16T16:30:00Z"));
    }

    @DisplayName("A catalog somebody manages is not added to; its own halls and movies get the showtimes")
    @Test
    void leavesAnExistingCatalogAlone() throws Exception
    {
        long movieId = createMovie("Arrival");
        long hallId = createHall("Hall 1");

        int added = programmeAt(MORNING, 2).topUp();

        assertThat(movieRepository.count()).isEqualTo(1);
        assertThat(hallRepository.count()).isEqualTo(1);
        assertThat(added).isEqualTo(8);
        Integer ofThatMovieInThatHall = jdbc.queryForObject(
                "SELECT count(*) FROM showtimes WHERE movie_id = ? AND hall_id = ?", Integer.class, movieId, hallId);
        assertThat(ofThatMovieInThatHall).isEqualTo(8);
    }

    @DisplayName("A day that already has a showtime in a hall is left as it is")
    @Test
    void leavesADayThatHasShowtimesAlone() throws Exception
    {
        long movieId = createMovie("Arrival");
        long hallId = createHall("Hall 1");
        // One showtime on the 16th, scheduled by hand.
        createShowtime(movieId, hallId, "2030-01-16T18:00:00Z", "2030-01-16T20:00:00Z");

        int added = programmeAt(MORNING, 2).topUp();

        // Only the 17th was filled.
        assertThat(added).isEqualTo(4);
        Integer onThe16th = jdbc.queryForObject(
                "SELECT count(*) FROM showtimes WHERE start_time < '2030-01-16T23:00:00Z'", Integer.class);
        assertThat(onThe16th).isEqualTo(1);
    }

    private DemoProgramme programmeAt(Instant now, int days)
    {
        return new DemoProgramme(new DemoProperties(true, days, ZONE), movieService, hallService, showtimeService,
                movieRepository, hallRepository, seatRepository, showtimeRepository, Clock.fixed(now, ZONE));
    }
}
