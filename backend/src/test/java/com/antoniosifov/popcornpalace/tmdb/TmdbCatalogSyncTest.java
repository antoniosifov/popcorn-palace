package com.antoniosifov.popcornpalace.tmdb;

import com.antoniosifov.popcornpalace.movie.MovieRepository;
import com.antoniosifov.popcornpalace.support.ApiIntegrationTest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The import of movies in cinemas, against the real database and a stand-in
 * for TMDB: a small HTTP server in this test that answers the two calls the
 * way TMDB does and remembers what it was asked.
 */
class TmdbCatalogSyncTest extends ApiIntegrationTest
{
    /** An access token has three dot-separated parts; this one is made up. */
    private static final String ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.c2lnbmF0dXJl";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-16T08:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private HttpServer tmdb;
    /** What the stand-in says is playing, in order; the test changes it between imports. */
    private final List<String> playing = new ArrayList<>();
    private final Map<Long, String> details = new LinkedHashMap<>();
    private final List<String> requests = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private int answerWith = 200;

    @BeforeEach
    void startTmdb() throws IOException
    {
        tmdb = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        tmdb.createContext("/3/movie/now_playing", exchange -> answer(exchange, """
                {"page": 1, "total_pages": 1, "total_results": %d, "results": [%s]}
                """.formatted(playing.size(), String.join(",", playing))));
        tmdb.createContext("/3/movie/", exchange -> {
            String id = exchange.getRequestURI().getPath().replaceAll(".*/", "");
            answer(exchange, details.getOrDefault(Long.valueOf(id), "{}"));
        });
        tmdb.start();

        nowPlaying(101, "Lanterns at Low Tide", false, "/poster101.jpg", 91.5);
        nowPlaying(102, "The Quiet Orbit", false, "/poster102.jpg", 80.0);
        details.put(101L, """
                {"id": 101, "title": "Lanterns at Low Tide", "overview": "A lighthouse keeper finds a message that should not exist.",
                 "tagline": "Some lights are warnings.", "runtime": 118, "popularity": 91.5,
                 "genres": [{"id": 9648, "name": "Mystery"}, {"id": 18, "name": "Drama"}, {"id": 53, "name": "Thriller"}],
                 "release_date": "2029-12-18", "vote_average": 7.449, "vote_count": 312,
                 "poster_path": "/poster101.jpg", "backdrop_path": "/backdrop101.jpg",
                 "budget": 40000000, "a_field_added_later": {"anything": true},
                 "videos": {"results": [
                     {"key": "teaser0001", "site": "YouTube", "type": "Teaser", "official": true},
                     {"key": "fanmade0001", "site": "YouTube", "type": "Trailer", "official": false},
                     {"key": "official001", "site": "YouTube", "type": "Trailer", "official": true},
                     {"key": "vimeo000001", "site": "Vimeo", "type": "Trailer", "official": true}]},
                 "credits": {
                     "cast": [{"name": "Third Billed", "order": 2}, {"name": "Lead Actor", "order": 0},
                              {"name": "Second Billed", "order": 1}, {"name": "Fourth Billed", "order": 3},
                              {"name": "Fifth Billed", "order": 4}],
                     "crew": [{"name": "A Producer", "job": "Producer"}, {"name": "Mira Castell", "job": "Director"}]}}
                """);
        details.put(102L, """
                {"id": 102, "title": "The Quiet Orbit", "overview": "Two engineers are left alone on a station that keeps correcting its course.",
                 "tagline": "", "runtime": 96, "popularity": 80.0, "genres": [{"id": 878, "name": "Science Fiction"}],
                 "release_date": "2030-01-04", "vote_average": 0.0, "vote_count": 0,
                 "poster_path": "/poster102.jpg", "backdrop_path": null, "videos": {"results": []}, "credits": {"cast": [], "crew": []}}
                """);
    }

    @AfterEach
    void stopTmdb()
    {
        tmdb.stop(0);
    }

    @DisplayName("Movies in cinemas come in with their description, artwork, trailer, cast and length")
    @Test
    void importsWhatIsPlaying() throws Exception
    {
        assertThat(sync(ACCESS_TOKEN, 16).sync()).isEqualTo(2);

        mvc.perform(get(MOVIES).param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Lanterns at Low Tide"))
                .andExpect(jsonPath("$.content[0].overview").value("A lighthouse keeper finds a message that should not exist."))
                .andExpect(jsonPath("$.content[0].tagline").value("Some lights are warnings."))
                // The first two genres; the length from the details, not the list.
                .andExpect(jsonPath("$.content[0].genre").value("Mystery, Drama"))
                .andExpect(jsonPath("$.content[0].durationMinutes").value(118))
                .andExpect(jsonPath("$.content[0].releaseYear").value(2029))
                // 7.449 out of ten is kept to one decimal.
                .andExpect(jsonPath("$.content[0].rating").value(7.4))
                .andExpect(jsonPath("$.content[0].posterUrl").value("https://image.tmdb.org/t/p/w500/poster101.jpg"))
                .andExpect(jsonPath("$.content[0].backdropUrl").value("https://image.tmdb.org/t/p/w1280/backdrop101.jpg"))
                // The official trailer on YouTube, not the teaser, the fan cut or the one hosted elsewhere.
                .andExpect(jsonPath("$.content[0].trailerUrl").value("https://www.youtube.com/watch?v=official001"))
                .andExpect(jsonPath("$.content[0].director").value("Mira Castell"))
                // The first four of the cast, in billing order.
                .andExpect(jsonPath("$.content[0].starring.length()").value(4))
                .andExpect(jsonPath("$.content[0].starring[0]").value("Lead Actor"))
                .andExpect(jsonPath("$.content[0].starring[3]").value("Fourth Billed"))
                .andExpect(jsonPath("$.content[0].inCinemas").value(true))
                .andExpect(jsonPath("$.content[0].popularity").value(91.5));
    }

    @DisplayName("A movie with gaps is still a movie: no tagline, backdrop, trailer or cast, and not yet rated")
    @Test
    void importsAMovieWithGaps() throws Exception
    {
        sync(ACCESS_TOKEN, 16).sync();

        mvc.perform(get(MOVIES).param("sort", "title,desc").param("size", "1"))
                .andExpect(jsonPath("$.content[0].title").value("The Quiet Orbit"))
                .andExpect(jsonPath("$.content[0].tagline").isEmpty())
                .andExpect(jsonPath("$.content[0].backdropUrl").isEmpty())
                .andExpect(jsonPath("$.content[0].trailerUrl").isEmpty())
                .andExpect(jsonPath("$.content[0].director").isEmpty())
                .andExpect(jsonPath("$.content[0].starring.length()").value(0))
                .andExpect(jsonPath("$.content[0].rating").value(0.0));
    }

    @DisplayName("Importing again adds nothing and refreshes what changed")
    @Test
    void isRepeatable()
    {
        TmdbCatalogSync sync = sync(ACCESS_TOKEN, 16);
        sync.sync();
        Long idBefore = movieRepository.findByTmdbId(101L).orElseThrow().getId();

        // Votes came in since.
        details.put(101L, details.get(101L).replace("\"vote_average\": 7.449", "\"vote_average\": 8.06"));
        assertThat(sync.sync()).isEqualTo(2);

        assertThat(movieRepository.count()).isEqualTo(2);
        var movie = movieRepository.findByTmdbId(101L).orElseThrow();
        assertThat(movie.getId()).isEqualTo(idBefore);
        assertThat(movie.getRating()).isEqualByComparingTo("8.1");
    }

    @DisplayName("A movie that has left the cinemas is marked, not removed: its showtimes and bookings stay")
    @Test
    void retiresWhatIsNoLongerPlaying() throws Exception
    {
        TmdbCatalogSync sync = sync(ACCESS_TOKEN, 16);
        sync.sync();
        long leaving = movieRepository.findByTmdbId(102L).orElseThrow().getId();
        long hallId = createHall("Hall 1");
        createShowtime(leaving, hallId, "2030-02-01T18:00:00Z", "2030-02-01T19:36:00Z");

        playing.remove(1);
        assertThat(sync.sync()).isEqualTo(1);

        mvc.perform(get(MOVIES + "/{id}", leaving))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inCinemas").value(false));
        assertThat(movieRepository.findByTmdbId(101L).orElseThrow().isInCinemas()).isTrue();
        mvc.perform(get(SHOWTIMES).param("movieId", String.valueOf(leaving)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @DisplayName("Movies for adults, without a poster, without a description or of unknown length are left out")
    @Test
    void skipsWhatAProgrammeCannotShow()
    {
        playing.clear();
        nowPlaying(201, "For Adults", true, "/p.jpg", 99.0);
        playing.add("""
                {"id": 202, "title": "No Poster", "adult": false, "overview": "Something happens.", "popularity": 98, "poster_path": null}""");
        playing.add("""
                {"id": 203, "title": "No Words", "adult": false, "overview": "", "popularity": 97, "poster_path": "/p.jpg"}""");
        nowPlaying(204, "Length Unknown", false, "/p.jpg", 96.0);
        details.put(204L, """
                {"id": 204, "title": "Length Unknown", "overview": "Not finished yet.", "runtime": 0, "poster_path": "/p.jpg"}""");
        nowPlaying(101, "Lanterns at Low Tide", false, "/poster101.jpg", 91.5);

        assertThat(sync(ACCESS_TOKEN, 16).sync()).isEqualTo(1);

        assertThat(movieRepository.findAll()).extracting("title").containsExactly("Lanterns at Low Tide");
        // Nothing was asked about the three that the list already ruled out.
        assertThat(requests).noneMatch(request -> request.matches(".*/movie/20[123]\\b.*"));
    }

    @DisplayName("Only as many movies as the programme takes are imported, the most popular first")
    @Test
    void takesNoMoreThanTheLimit()
    {
        assertThat(sync(ACCESS_TOKEN, 1).sync()).isEqualTo(1);

        assertThat(movieRepository.findAll()).extracting("title").containsExactly("Lanterns at Low Tide");
    }

    @DisplayName("A newcomer with the title of a movie already in the catalog gets its year, not a refusal")
    @Test
    void keepsTitlesUnique() throws Exception
    {
        createMovie("The Quiet Orbit");

        sync(ACCESS_TOKEN, 16).sync();

        assertThat(movieRepository.findAll()).extracting("title")
                .containsExactlyInAnyOrder("The Quiet Orbit", "The Quiet Orbit (2030)", "Lanterns at Low Tide");
        // And it keeps that title the next time, instead of fighting for the plain one.
        sync(ACCESS_TOKEN, 16).sync();
        assertThat(movieRepository.count()).isEqualTo(3);
    }

    @DisplayName("An access token travels in the Authorization header, with the language and the region asked for")
    @Test
    void sendsTheAccessTokenAsAHeader()
    {
        sync(ACCESS_TOKEN, 16).sync();

        assertThat(authorizations).isNotEmpty().allMatch(header -> header.equals("Bearer " + ACCESS_TOKEN));
        assertThat(requests.getFirst()).isEqualTo("/3/movie/now_playing?language=en-US&region=US&page=1");
        assertThat(requests).contains("/3/movie/101?language=en-US&append_to_response=videos,credits");
        assertThat(requests).noneMatch(request -> request.contains("api_key"));
    }

    @DisplayName("The older, shorter key travels as a query parameter")
    @Test
    void sendsTheOlderKeyAsAParameter()
    {
        sync("0123456789abcdef0123456789abcdef", 16).sync();

        assertThat(requests).isNotEmpty().allMatch(request -> request.endsWith("api_key=0123456789abcdef0123456789abcdef"));
        assertThat(authorizations).allMatch(String::isEmpty);
    }

    @DisplayName("Without a key nothing is asked and nothing changes")
    @Test
    void isOffWithoutAKey() throws Exception
    {
        createMovie("Arrival");

        assertThat(sync("", 16).sync()).isEqualTo(-1);
        assertThat(sync(null, 16).sync()).isEqualTo(-1);

        assertThat(requests).isEmpty();
        assertThat(movieRepository.count()).isEqualTo(1);
    }

    @DisplayName("When TMDB refuses the key or is down, the catalog stays as it was and nothing is thrown")
    @Test
    void survivesTmdbFailing()
    {
        TmdbCatalogSync sync = sync(ACCESS_TOKEN, 16);
        sync.sync();

        for (int failure : new int[] {401, 500}) {
            answerWith = failure;
            assertThat(sync.sync()).isEqualTo(-1);
        }
        tmdb.stop(0);
        assertThat(sync.sync()).isEqualTo(-1);

        assertThat(movieRepository.findAllByInCinemasTrueOrderByPopularityDescIdAsc()).hasSize(2);
    }

    @DisplayName("An empty list from TMDB is treated as a mistake: nobody is retired because of it")
    @Test
    void doesNotRetireEverythingOnAnEmptyAnswer()
    {
        TmdbCatalogSync sync = sync(ACCESS_TOKEN, 16);
        sync.sync();

        playing.clear();
        assertThat(sync.sync()).isEqualTo(-1);

        assertThat(movieRepository.findAllByInCinemasTrueOrderByPopularityDescIdAsc()).hasSize(2);
    }

    // ------------------------------------------------------------ helpers

    private TmdbCatalogSync sync(String key, int limit)
    {
        TmdbProperties properties = new TmdbProperties(key, "http://localhost:" + tmdb.getAddress().getPort() + "/3",
                "https://image.tmdb.org/t/p", "en-US", "US", limit, Duration.ofHours(12));
        return new TmdbCatalogSync(new TmdbClient(properties, ObservationRegistry.NOOP), properties, movieRepository,
                transactionManager, CLOCK);
    }

    private void nowPlaying(long id, String title, boolean adult, String poster, double popularity)
    {
        playing.add("""
                {"id": %d, "title": "%s", "adult": %s, "overview": "A description.", "popularity": %s,
                 "poster_path": "%s", "genre_ids": [18], "vote_average": 6.5}""".formatted(id, title, adult, popularity, poster));
    }

    private void answer(HttpExchange exchange, String json) throws IOException
    {
        String query = exchange.getRequestURI().getRawQuery();
        requests.add(exchange.getRequestURI().getPath() + (query == null ? "" : "?" + java.net.URLDecoder.decode(query, StandardCharsets.UTF_8)));
        authorizations.add(String.join("", exchange.getRequestHeaders().getOrDefault("Authorization", List.of(""))));
        byte[] body = (answerWith == 200 ? json : """
                {"status_code": 7, "status_message": "Invalid API key: You must be granted a valid key.", "success": false}""")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=utf-8");
        exchange.sendResponseHeaders(answerWith, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
