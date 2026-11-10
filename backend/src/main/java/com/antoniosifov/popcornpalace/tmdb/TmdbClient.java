package com.antoniosifov.popcornpalace.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * The two calls this application makes to TMDB (The Movie Database): the list
 * of movies playing in cinemas, and everything about one movie.
 *
 * <p>Only the fields that are used are read; TMDB may add to its answers
 * without breaking this.
 */
@Component
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbClient
{
    /** A slow or silent TMDB must not hold a thread for long: the import simply runs again later. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient http;
    private final TmdbProperties properties;
    /** The older, shorter key travels as a query parameter; the access token as a header. */
    private final String queryKey;

    public TmdbClient(TmdbProperties properties, ObservationRegistry observations)
    {
        this.properties = properties;
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(CONNECT_TIMEOUT);
        requests.setReadTimeout(READ_TIMEOUT);

        String key = properties.enabled() ? properties.apiKey().strip() : "";
        // An access token is a signed token of three dot-separated parts; a key is 32 hex characters.
        boolean accessToken = key.chars().filter(character -> character == '.').count() == 2;
        this.queryKey = accessToken ? null : key;

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requests)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json");
        if (accessToken) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    // Calls to TMDB show up in traces and in the request metrics like any other.
                    .observationRegistry(observations);
        }
        // With the older key the address of every request contains the key, and a
        // span records the address: those calls are deliberately left out of traces.
        this.http = builder.build();
    }

    /** One page (twenty movies) of what is playing in cinemas, the most popular first. */
    public NowPlaying nowPlaying(int page)
    {
        return http.get()
                .uri(uri -> {
                    uri.path("/movie/now_playing")
                            .queryParam("language", properties.language())
                            .queryParam("region", properties.region())
                            .queryParam("page", page);
                    if (queryKey != null) {
                        uri.queryParam("api_key", queryKey);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(NowPlaying.class);
    }

    /** One movie with its trailers and its cast and crew. */
    public Details details(long movieId)
    {
        return http.get()
                .uri(uri -> {
                    uri.path("/movie/{id}")
                            .queryParam("language", properties.language())
                            .queryParam("append_to_response", "videos,credits");
                    if (queryKey != null) {
                        uri.queryParam("api_key", queryKey);
                    }
                    return uri.build(movieId);
                })
                .retrieve()
                .body(Details.class);
    }

    // ------------------------------------------------------------ what TMDB answers

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NowPlaying(int page, List<Listed> results, @JsonProperty("total_pages") int totalPages)
    {
        public NowPlaying
        {
            results = results == null ? List.of() : results;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Listed(
            long id,
            String title,
            boolean adult,
            String overview,
            Double popularity,
            @JsonProperty("poster_path") String posterPath
    )
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Details(
            long id,
            String title,
            String overview,
            String tagline,
            Integer runtime,
            List<Genre> genres,
            Double popularity,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("vote_average") Double voteAverage,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            Videos videos,
            Credits credits
    )
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(String name)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Videos(List<Video> results)
    {
    }

    /** @param site where the video is hosted, "YouTube" for nearly all of them; {@code key} is its id there */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Video(String key, String site, String type, boolean official)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credits(List<Cast> cast, List<Crew> crew)
    {
    }

    /** @param order the position in the billing, 0 for the lead */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cast(String name, Integer order)
    {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Crew(String name, String job)
    {
    }
}
