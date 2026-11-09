package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.web.PageResponse;
import com.antoniosifov.popcornpalace.common.web.SortFields;
import com.antoniosifov.popcornpalace.movie.Movie;
import com.antoniosifov.popcornpalace.movie.MovieRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ShowtimeService
{
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "theater", "startTime", "endTime", "price");

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;

    public ShowtimeService(ShowtimeRepository showtimeRepository, MovieRepository movieRepository)
    {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
    }

    /** Lists showtimes, optionally only those of one movie. */
    public PageResponse<ShowtimeResponse> list(Long movieId, Pageable pageable)
    {
        SortFields.requireAllowed(pageable, SORTABLE_FIELDS);
        Page<Showtime> page = movieId == null
                ? showtimeRepository.findAll(pageable)
                : showtimeRepository.findAllByMovieId(movieId, pageable);
        return PageResponse.from(page.map(ShowtimeResponse::from));
    }

    public ShowtimeResponse get(Long id)
    {
        return ShowtimeResponse.from(require(id));
    }

    @Transactional
    public ShowtimeResponse create(ShowtimeRequest request)
    {
        Movie movie = requireMovie(request.movieId());
        if (showtimeRepository.existsOverlapping(request.theater(), request.startTime(), request.endTime())) {
            throw overlap(request.theater());
        }
        Showtime showtime = new Showtime(movie, request.theater(), request.startTime(), request.endTime(), request.price());
        return ShowtimeResponse.from(showtimeRepository.saveAndFlush(showtime));
    }

    @Transactional
    public ShowtimeResponse update(Long id, ShowtimeRequest request)
    {
        Showtime showtime = require(id);
        Movie movie = requireMovie(request.movieId());
        if (showtimeRepository.existsOverlappingExcluding(request.theater(), request.startTime(), request.endTime(), id)) {
            throw overlap(request.theater());
        }
        showtime.update(movie, request.theater(), request.startTime(), request.endTime(), request.price());
        return ShowtimeResponse.from(showtimeRepository.saveAndFlush(showtime));
    }

    /**
     * A showtime with bookings cannot be deleted. The foreign key
     * fk_bookings_showtime enforces this; the violation is reported as a conflict.
     */
    @Transactional
    public void delete(Long id)
    {
        showtimeRepository.delete(require(id));
        showtimeRepository.flush();
    }

    private Showtime require(Long id)
    {
        return showtimeRepository.findWithMovieById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND, "Showtime %d was not found".formatted(id)));
    }

    private Movie requireMovie(Long movieId)
    {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.MOVIE_NOT_FOUND, "Movie %d was not found".formatted(movieId)));
    }

    private static ApiException overlap(String theater)
    {
        return ApiException.conflict(ErrorCode.SHOWTIME_OVERLAP,
                "Another showtime is already scheduled in '%s' during this time".formatted(theater));
    }
}
