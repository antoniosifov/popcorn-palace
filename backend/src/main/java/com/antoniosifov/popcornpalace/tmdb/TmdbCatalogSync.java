package com.antoniosifov.popcornpalace.tmdb;

import com.antoniosifov.popcornpalace.common.text.Text;
import com.antoniosifov.popcornpalace.movie.Movie;
import com.antoniosifov.popcornpalace.movie.MoviePresentation;
import com.antoniosifov.popcornpalace.movie.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps the catalog in step with what is playing in cinemas, according to
 * TMDB: new releases come in with their poster, description, trailer and
 * cast; movies that are still playing are refreshed; movies that have left
 * are marked, not removed, because bookings point at them.
 *
 * <p>Off without a key ({@code TMDB_API_KEY}). It runs at startup, before the
 * demo programme schedules showtimes, and then at an interval. Whatever goes
 * wrong with TMDB (down, slow, a rejected key) is logged and leaves the
 * catalog as it was: the cinema keeps selling tickets without it.
 *
 * <p>A movie is recognised by its TMDB id, so the import can run any number
 * of times, on any number of instances, without adding a movie twice; the
 * unique index on that id is what guarantees it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TmdbCatalogSync implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(TmdbCatalogSync.class);

    /** A page of TMDB holds twenty movies; this is how far the import looks for enough usable ones. */
    private static final int MAX_PAGES = 5;
    private static final int STARRING = 4;
    private static final String POSTER_SIZE = "/w500";
    private static final String BACKDROP_SIZE = "/w1280";

    private final TmdbClient tmdb;
    private final TmdbProperties properties;
    private final MovieRepository movies;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public TmdbCatalogSync(TmdbClient tmdb, TmdbProperties properties, MovieRepository movies,
                           PlatformTransactionManager transactionManager, Clock clock)
    {
        this.tmdb = tmdb;
        this.properties = properties;
        this.movies = movies;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        sync();
    }

    @Scheduled(fixedDelayString = "${app.tmdb.sync-interval}", initialDelayString = "${app.tmdb.sync-interval}")
    void syncAgain()
    {
        sync();
    }

    /**
     * Brings the catalog up to date and returns how many movies are playing
     * now, or -1 when nothing was done (no key, or TMDB could not be asked).
     */
    public int sync()
    {
        if (!properties.enabled()) {
            return -1;
        }
        try {
            List<TmdbClient.Details> playing = fetchPlaying();
            if (playing.isEmpty()) {
                // An empty answer is far more likely a mistake than an empty cinema: change nothing.
                log.warn("TMDB returned no usable movies for region {}; the catalog is left as it is", properties.region());
                return -1;
            }
            Set<Long> ids = new HashSet<>();
            for (TmdbClient.Details details : playing) {
                try {
                    transaction.executeWithoutResult(status -> store(details));
                }
                catch (DataAccessException refused) {
                    // One movie the database will not take (another instance stored it
                    // a moment ago, or its title clashes twice) must not stop the rest.
                    log.warn("TMDB: movie {} was not stored ({})", details.id(), refused.getClass().getSimpleName());
                }
                // Stored or not, it is playing: it must not be retired below.
                ids.add(details.id());
            }
            transaction.executeWithoutResult(status -> retireOthers(ids));
            log.info("TMDB: {} movies in cinemas", ids.size());
            return ids.size();
        }
        catch (RuntimeException failure) {
            // The message of an HTTP failure names the status and the address path, never the key header.
            log.warn("TMDB could not be asked for the movies in cinemas; the catalog is left as it is ({})",
                    failure.getClass().getSimpleName());
            return -1;
        }
    }

    /** The most popular movies in cinemas that have what a programme needs: a poster, a description, a length. */
    private List<TmdbClient.Details> fetchPlaying()
    {
        List<TmdbClient.Details> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int page = 1; page <= MAX_PAGES && result.size() < properties.limit(); page++) {
            TmdbClient.NowPlaying listing = tmdb.nowPlaying(page);
            for (TmdbClient.Listed listed : listing.results()) {
                if (result.size() >= properties.limit()) {
                    break;
                }
                if (listed.adult() || isBlank(listed.posterPath()) || isBlank(listed.overview()) || !seen.add(listed.id())) {
                    continue;
                }
                TmdbClient.Details details = tmdb.details(listed.id());
                if (details != null && details.runtime() != null && details.runtime() > 0 && !isBlank(details.title())) {
                    result.add(details);
                }
            }
            if (page >= listing.totalPages()) {
                break;
            }
        }
        return result;
    }

    private void store(TmdbClient.Details details)
    {
        Movie movie = movies.findByTmdbId(details.id()).orElse(null);
        String title = uniqueTitle(Text.normalize(details.title()), releaseYear(details), movie);
        MoviePresentation presentation = new MoviePresentation(
                limit(Text.normalize(details.overview()), 4000),
                limit(blankToNull(Text.normalize(details.tagline())), 300),
                image(POSTER_SIZE, details.posterPath()),
                image(BACKDROP_SIZE, details.backdropPath()),
                trailer(details),
                limit(director(details), 200),
                starring(details));
        if (movie == null) {
            movie = new Movie(title, genre(details), details.runtime(), rating(details), releaseYear(details), presentation);
        }
        else {
            movie.update(title, genre(details), details.runtime(), rating(details), releaseYear(details), presentation);
        }
        movie.importedFrom(details.id(), details.popularity());
        movies.save(movie);
    }

    /** Whatever was imported earlier and is not in the list any more has left the cinemas. */
    private void retireOthers(Set<Long> playing)
    {
        for (Movie movie : movies.findAllByInCinemasTrueOrderByPopularityDescIdAsc()) {
            if (movie.getTmdbId() != null && !playing.contains(movie.getTmdbId())) {
                movie.leaveCinemas();
            }
        }
    }

    /**
     * Titles are unique in the catalog, and two different movies can share one
     * (a remake, or a movie somebody added by hand). The newcomer gets its
     * year appended rather than being refused.
     */
    private String uniqueTitle(String title, int year, Movie self)
    {
        String candidate = limit(title, 190);
        Movie holder = movies.findByTitle(candidate).orElse(null);
        if (holder == null || (self != null && holder.getId().equals(self.getId()))) {
            return candidate;
        }
        return "%s (%d)".formatted(candidate, year);
    }

    private String image(String size, String path)
    {
        return isBlank(path) ? null : properties.imageBaseUrl() + size + path;
    }

    /** The official trailer on YouTube when there is one, otherwise any trailer there. */
    private static String trailer(TmdbClient.Details details)
    {
        if (details.videos() == null || details.videos().results() == null) {
            return null;
        }
        return details.videos().results().stream()
                .filter(video -> "YouTube".equalsIgnoreCase(video.site()) && "Trailer".equalsIgnoreCase(video.type()))
                .filter(video -> video.key() != null && video.key().matches("[A-Za-z0-9_-]{6,20}"))
                .min(Comparator.comparing((TmdbClient.Video video) -> !video.official()))
                .map(video -> "https://www.youtube.com/watch?v=" + video.key())
                .orElse(null);
    }

    private static String director(TmdbClient.Details details)
    {
        if (details.credits() == null || details.credits().crew() == null) {
            return null;
        }
        return details.credits().crew().stream()
                .filter(member -> "Director".equals(member.job()) && !isBlank(member.name()))
                .map(member -> Text.normalize(member.name()))
                .findFirst()
                .orElse(null);
    }

    private static List<String> starring(TmdbClient.Details details)
    {
        if (details.credits() == null || details.credits().cast() == null) {
            return List.of();
        }
        return details.credits().cast().stream()
                .filter(member -> !isBlank(member.name()))
                .sorted(Comparator.comparing(member -> Objects.requireNonNullElse(member.order(), Integer.MAX_VALUE)))
                .limit(STARRING)
                .map(member -> limit(Text.normalize(member.name()), 70))
                .toList();
    }

    /** The first two genres, which is what fits on a line: "Science Fiction, Adventure". */
    private static String genre(TmdbClient.Details details)
    {
        if (details.genres() == null || details.genres().isEmpty()) {
            return "Feature";
        }
        String joined = details.genres().stream()
                .map(TmdbClient.Genre::name)
                .filter(name -> !isBlank(name))
                .limit(2)
                .reduce((first, second) -> first + ", " + second)
                .orElse("Feature");
        return limit(joined, 100);
    }

    /** TMDB rates out of ten with many decimals; the catalog keeps one. A movie nobody has rated yet is 0.0. */
    private static BigDecimal rating(TmdbClient.Details details)
    {
        double average = details.voteAverage() == null ? 0 : Math.max(0, Math.min(10, details.voteAverage()));
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }

    private int releaseYear(TmdbClient.Details details)
    {
        String date = details.releaseDate();
        if (date != null && date.matches("\\d{4}-.*")) {
            int year = Integer.parseInt(date.substring(0, 4));
            if (year >= 1888 && year <= 2100) {
                return year;
            }
        }
        return Year.now(clock).getValue();
    }

    private static String limit(String value, int length)
    {
        return value == null || value.length() <= length ? value : value.substring(0, length - 1).stripTrailing() + "…";
    }

    private static String blankToNull(String value)
    {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }
}
