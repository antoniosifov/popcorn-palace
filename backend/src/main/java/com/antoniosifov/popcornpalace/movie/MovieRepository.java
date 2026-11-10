package com.antoniosifov.popcornpalace.movie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long>
{
    boolean existsByTitle(String title);

    boolean existsByTitleAndIdNot(String title, Long id);

    Optional<Movie> findByTitle(String title);

    /** The movie imported from TMDB under that id, if it was. */
    Optional<Movie> findByTmdbId(Long tmdbId);

    /** What the latest import found playing, the most popular first. */
    List<Movie> findAllByInCinemasTrueOrderByPopularityDescIdAsc();
}
