package com.antoniosifov.popcornpalace.demo;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.hall.Hall;
import com.antoniosifov.popcornpalace.hall.HallRepository;
import com.antoniosifov.popcornpalace.hall.HallRequest;
import com.antoniosifov.popcornpalace.hall.HallService;
import com.antoniosifov.popcornpalace.hall.SeatCategory;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.movie.Movie;
import com.antoniosifov.popcornpalace.movie.MovieRepository;
import com.antoniosifov.popcornpalace.movie.MovieRequest;
import com.antoniosifov.popcornpalace.movie.MovieService;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRepository;
import com.antoniosifov.popcornpalace.showtime.ShowtimeRequest;
import com.antoniosifov.popcornpalace.showtime.ShowtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a cinema programme in an otherwise empty installation, so the product
 * has something to show: a few halls, a few movies, and showtimes for today
 * and the days ahead. Off unless {@code app.demo.enabled} is set (docker
 * compose sets it).
 *
 * <p>It runs at startup and then once an hour, and only ever adds:
 *
 * <ul>
 *   <li>halls and movies are created only while there are none at all, so a
 *       catalog somebody manages by hand, or one imported from TMDB, is never
 *       touched;</li>
 *   <li>when the catalog knows which movies are in cinemas (the TMDB import
 *       says so), only those get new showtimes;</li>
 *   <li>a hall gets a day of showtimes only if nothing starts in it that day,
 *       so the programme rolls forward one day at a time and leaves alone
 *       every day that already has something, whoever scheduled it.</li>
 * </ul>
 *
 * <p>Everything is created through the same services the API uses, so the
 * demo data obeys every rule real data does. Two instances starting together
 * cannot double anything: the unique names and the rule against overlapping
 * showtimes turn the second attempt into a conflict, which is skipped.
 */
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@EnableConfigurationProperties(DemoProperties.class)
public class DemoProgramme implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(DemoProgramme.class);

    /** When a showtime starts, on the cinema's clock, and whether it is priced as a matinee. */
    private record Slot(LocalTime start, boolean matinee)
    {
    }

    private static final List<Slot> SLOTS = List.of(
            new Slot(LocalTime.of(11, 0), true),
            new Slot(LocalTime.of(14, 15), true),
            new Slot(LocalTime.of(17, 30), false),
            new Slot(LocalTime.of(20, 45), false));

    private static final Map<SeatCategory, BigDecimal> MATINEE_PRICES = Map.of(
            SeatCategory.STANDARD, new BigDecimal("11.00"),
            SeatCategory.VIP, new BigDecimal("18.00"));
    private static final Map<SeatCategory, BigDecimal> EVENING_PRICES = Map.of(
            SeatCategory.STANDARD, new BigDecimal("14.50"),
            SeatCategory.VIP, new BigDecimal("22.00"));

    private static final List<HallRequest> HALLS = List.of(
            new HallRequest("Grand Hall", List.of(
                    new HallRequest.Section(8, 14, SeatCategory.STANDARD),
                    new HallRequest.Section(2, 12, SeatCategory.VIP))),
            new HallRequest("Studio", List.of(
                    new HallRequest.Section(6, 10, SeatCategory.STANDARD),
                    new HallRequest.Section(2, 8, SeatCategory.VIP))),
            new HallRequest("Screening Room", List.of(
                    new HallRequest.Section(5, 8, SeatCategory.STANDARD))));

    /**
     * The built-in catalog, for an installation without a TMDB key. No posters:
     * artwork is not this project's to hand out, so the client draws title cards.
     */
    private static final List<MovieRequest> MOVIES = List.of(
            movie("Interstellar", "Science Fiction", 169, "8.7", 2014,
                    "With Earth running out of food, a former pilot leaves his children to fly through a wormhole "
                            + "in search of a planet people could live on, while time passes far faster at home.",
                    "Christopher Nolan", "Matthew McConaughey", "Anne Hathaway", "Jessica Chastain"),
            movie("Spirited Away", "Animation", 125, "8.6", 2001,
                    "A ten-year-old girl wanders into a bathhouse for spirits, where her parents are turned into pigs "
                            + "and she has to work, and keep her name, to free them.",
                    "Hayao Miyazaki", "Rumi Hiiragi", "Miyu Irino", "Mari Natsuki"),
            movie("Parasite", "Thriller", 132, "8.5", 2019,
                    "A family living in a basement flat talks its way, one job at a time, into the home of a wealthy "
                            + "household, until somebody else turns out to live there too.",
                    "Bong Joon Ho", "Song Kang-ho", "Lee Sun-kyun", "Cho Yeo-jeong"),
            movie("Dune: Part Two", "Science Fiction", 166, "8.5", 2024,
                    "Paul Atreides joins the Fremen of the desert planet Arrakis to take revenge on those who "
                            + "destroyed his family, and is drawn toward a war he has seen coming.",
                    "Denis Villeneuve", "Timothée Chalamet", "Zendaya", "Rebecca Ferguson"),
            movie("Whiplash", "Drama", 106, "8.5", 2014,
                    "A young drummer at a top conservatory is pushed past every limit by a teacher who believes "
                            + "that greatness only comes out of fear.",
                    "Damien Chazelle", "Miles Teller", "J.K. Simmons"),
            movie("The Grand Budapest Hotel", "Comedy", 99, "8.1", 2014,
                    "The concierge of a famous hotel between the wars and his lobby boy are caught up in the theft "
                            + "of a priceless painting and the fight over a family fortune.",
                    "Wes Anderson", "Ralph Fiennes", "Tony Revolori", "Saoirse Ronan"),
            movie("Mad Max: Fury Road", "Action", 120, "8.1", 2015,
                    "In a desert wasteland, a drifter and a rebel driver flee a warlord in an armoured truck, "
                            + "carrying the women he kept captive.",
                    "George Miller", "Tom Hardy", "Charlize Theron", "Nicholas Hoult"),
            movie("Blade Runner 2049", "Science Fiction", 164, "8.0", 2017,
                    "A new blade runner digs up a secret that could end what is left of order, and goes looking "
                            + "for a predecessor who vanished thirty years ago.",
                    "Denis Villeneuve", "Ryan Gosling", "Harrison Ford", "Ana de Armas"));

    private final DemoProperties properties;
    private final MovieService movieService;
    private final HallService hallService;
    private final ShowtimeService showtimeService;
    private final MovieRepository movieRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final Clock clock;

    public DemoProgramme(DemoProperties properties, MovieService movieService, HallService hallService,
                         ShowtimeService showtimeService, MovieRepository movieRepository,
                         HallRepository hallRepository, SeatRepository seatRepository,
                         ShowtimeRepository showtimeRepository, Clock clock)
    {
        this.properties = properties;
        this.movieService = movieService;
        this.hallService = hallService;
        this.showtimeService = showtimeService;
        this.movieRepository = movieRepository;
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        topUp();
    }

    /** New days come into view as time passes; give them showtimes too. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    void keepUp()
    {
        topUp();
    }

    /**
     * Brings the programme up to date and returns how many showtimes were added.
     * Safe to call at any time, any number of times.
     */
    public int topUp()
    {
        createCatalogIfEmpty();

        List<Hall> halls = hallRepository.findAll(Sort.by("id"));
        List<Movie> movies = moviesToShow();
        if (halls.isEmpty() || movies.isEmpty()) {
            return 0;
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, properties.zone());
        int added = 0;
        for (int offset = 0; offset < properties.days(); offset++) {
            LocalDate day = today.plusDays(offset);
            for (int hallIndex = 0; hallIndex < halls.size(); hallIndex++) {
                added += scheduleDay(halls.get(hallIndex), hallIndex, day, movies, now);
            }
        }
        if (added > 0) {
            log.info("Demo programme: scheduled {} showtimes", added);
        }
        return added;
    }

    /**
     * What gets showtimes: the movies in cinemas, the most popular first, when
     * the catalog knows which those are; otherwise every movie there is.
     */
    private List<Movie> moviesToShow()
    {
        List<Movie> inCinemas = movieRepository.findAllByInCinemasTrueOrderByPopularityDescIdAsc();
        return inCinemas.isEmpty() ? movieRepository.findAll(Sort.by("id")) : inCinemas;
    }

    private void createCatalogIfEmpty()
    {
        if (hallRepository.count() == 0) {
            for (HallRequest hall : HALLS) {
                attempt(() -> hallService.create(hall));
            }
        }
        if (movieRepository.count() == 0) {
            for (MovieRequest movie : MOVIES) {
                attempt(() -> movieService.create(movie));
            }
        }
    }

    /** Schedules one day in one hall, unless something already starts there that day. */
    private int scheduleDay(Hall hall, int hallIndex, LocalDate day, List<Movie> movies, Instant now)
    {
        Instant dayStart = day.atStartOfDay(properties.zone()).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(properties.zone()).toInstant();
        if (showtimeRepository.existsByHallIdAndStartTimeGreaterThanEqualAndStartTimeLessThan(hall.getId(), dayStart, dayEnd)) {
            return 0;
        }

        Set<SeatCategory> categories = seatRepository.findCategoriesByHallId(hall.getId());
        int added = 0;
        for (int slotIndex = 0; slotIndex < SLOTS.size(); slotIndex++) {
            Slot slot = SLOTS.get(slotIndex);
            Instant start = day.atTime(slot.start()).atZone(properties.zone()).toInstant();
            // Nobody can book a showtime that has already started.
            if (!start.isAfter(now)) {
                continue;
            }
            Movie movie = movies.get(rotation(day, hallIndex, slotIndex, movies.size()));
            Instant end = start.plus(Duration.ofMinutes(movie.getDurationMinutes()));
            ShowtimeRequest request = new ShowtimeRequest(movie.getId(), hall.getId(), start, end,
                    pricesFor(categories, slot.matinee() ? MATINEE_PRICES : EVENING_PRICES));
            if (attempt(() -> showtimeService.create(request))) {
                added++;
            }
        }
        return added;
    }

    /** A price for each kind of seat the hall has, and for no other: a hall without VIP seats has no VIP price. */
    private static Map<SeatCategory, BigDecimal> pricesFor(Set<SeatCategory> categories, Map<SeatCategory, BigDecimal> prices)
    {
        Map<SeatCategory, BigDecimal> result = new EnumMap<>(SeatCategory.class);
        for (SeatCategory category : categories) {
            result.put(category, prices.get(category));
        }
        return result;
    }

    /**
     * Which movie plays in a slot. Depends only on the day, the hall and the
     * slot, so the same day always gets the same programme, every movie comes
     * up, and no two halls show the same movie at the same time.
     */
    private static int rotation(LocalDate day, int hallIndex, int slotIndex, int movieCount)
    {
        long position = day.toEpochDay() * 3 + slotIndex + hallIndex * 3L;
        return (int) Math.floorMod(position, (long) movieCount);
    }

    /**
     * Runs one creation and reports whether it happened. A refusal by a business
     * rule or by a database constraint (the name exists, the time is taken,
     * usually because another instance got there first) is expected and skipped.
     */
    private static boolean attempt(Runnable creation)
    {
        try {
            creation.run();
            return true;
        }
        catch (ApiException | DataAccessException refused) {
            log.debug("Demo programme: skipped ({})", refused.getMessage());
            return false;
        }
    }

    private static MovieRequest movie(String title, String genre, int durationMinutes, String rating, int releaseYear,
                                      String overview, String director, String... starring)
    {
        return new MovieRequest(title, genre, durationMinutes, new BigDecimal(rating), releaseYear,
                overview, null, null, null, null, director, List.of(starring));
    }
}
