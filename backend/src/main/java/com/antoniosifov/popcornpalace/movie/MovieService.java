package com.antoniosifov.popcornpalace.movie;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.web.PageResponse;
import com.antoniosifov.popcornpalace.common.web.SortFields;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class MovieService
{
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "title", "genre", "durationMinutes", "rating", "releaseYear");

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository)
    {
        this.movieRepository = movieRepository;
    }

    public PageResponse<MovieResponse> list(Pageable pageable)
    {
        SortFields.requireAllowed(pageable, SORTABLE_FIELDS);
        return PageResponse.from(movieRepository.findAll(pageable).map(MovieResponse::from));
    }

    public MovieResponse get(Long id)
    {
        return MovieResponse.from(require(id));
    }

    @Transactional
    public MovieResponse create(MovieRequest request)
    {
        if (movieRepository.existsByTitle(request.title())) {
            throw titleTaken(request.title());
        }
        Movie movie = new Movie(request.title(), request.genre(), request.durationMinutes(),
                request.rating(), request.releaseYear(), request.presentation());
        // Flush now so a concurrent duplicate fails here, on the uq_movies_title
        // constraint, and is reported as a conflict rather than at commit time.
        return MovieResponse.from(movieRepository.saveAndFlush(movie));
    }

    @Transactional
    public MovieResponse update(Long id, MovieRequest request)
    {
        Movie movie = require(id);
        if (movieRepository.existsByTitleAndIdNot(request.title(), id)) {
            throw titleTaken(request.title());
        }
        movie.update(request.title(), request.genre(), request.durationMinutes(),
                request.rating(), request.releaseYear(), request.presentation());
        return MovieResponse.from(movieRepository.saveAndFlush(movie));
    }

    /**
     * A movie that still has showtimes cannot be deleted. The foreign key
     * fk_showtimes_movie enforces this; the violation is reported as a conflict.
     */
    @Transactional
    public void delete(Long id)
    {
        movieRepository.delete(require(id));
        movieRepository.flush();
    }

    private Movie require(Long id)
    {
        return movieRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.MOVIE_NOT_FOUND, "Movie %d was not found".formatted(id)));
    }

    private static ApiException titleTaken(String title)
    {
        return ApiException.conflict(ErrorCode.MOVIE_TITLE_TAKEN, "A movie titled '%s' already exists".formatted(title));
    }
}
