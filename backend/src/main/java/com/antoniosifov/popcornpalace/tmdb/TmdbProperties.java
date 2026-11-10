package com.antoniosifov.popcornpalace.tmdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code app.tmdb} in application.yaml.
 *
 * @param apiKey       the "API Read Access Token" of a TMDB account, or its older, shorter "API key".
 *                     Empty switches the import off.
 * @param baseUrl      where the TMDB API is
 * @param imageBaseUrl where TMDB serves pictures from; a size and a file path are appended to it
 * @param language     the language of titles and descriptions, as a locale such as en-US
 * @param region       the country whose cinemas are meant, as an ISO 3166-1 code such as US
 * @param limit        how many movies the programme takes, the most popular first
 * @param syncInterval how often the list is fetched again
 */
@ConfigurationProperties("app.tmdb")
public record TmdbProperties(
        String apiKey,
        String baseUrl,
        String imageBaseUrl,
        String language,
        String region,
        int limit,
        Duration syncInterval
)
{
    public boolean enabled()
    {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Never the key itself: this is what ends up in logs. */
    @Override
    public String toString()
    {
        return "TmdbProperties[enabled=%s, baseUrl=%s, language=%s, region=%s, limit=%d]"
                .formatted(enabled(), baseUrl, language, region, limit);
    }
}
