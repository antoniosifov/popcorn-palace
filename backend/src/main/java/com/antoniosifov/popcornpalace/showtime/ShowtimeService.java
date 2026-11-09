package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.common.error.ApiException;
import com.antoniosifov.popcornpalace.common.error.ErrorCode;
import com.antoniosifov.popcornpalace.common.web.PageResponse;
import com.antoniosifov.popcornpalace.common.web.SortFields;
import com.antoniosifov.popcornpalace.hall.Hall;
import com.antoniosifov.popcornpalace.hall.HallRepository;
import com.antoniosifov.popcornpalace.hall.SeatCategory;
import com.antoniosifov.popcornpalace.hall.SeatRepository;
import com.antoniosifov.popcornpalace.movie.Movie;
import com.antoniosifov.popcornpalace.movie.MovieRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ShowtimeService
{
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "startTime", "endTime");

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;

    public ShowtimeService(ShowtimeRepository showtimeRepository, MovieRepository movieRepository,
                           HallRepository hallRepository, SeatRepository seatRepository)
    {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
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
        Hall hall = requireHall(request.hallId());
        requirePriceForEveryCategory(hall, request.prices());
        if (showtimeRepository.existsOverlapping(hall.getId(), request.startTime(), request.endTime())) {
            throw overlap(hall);
        }
        Showtime showtime = new Showtime(movie, hall, request.startTime(), request.endTime(), request.prices());
        return ShowtimeResponse.from(showtimeRepository.saveAndFlush(showtime));
    }

    @Transactional
    public ShowtimeResponse update(Long id, ShowtimeRequest request)
    {
        Showtime showtime = require(id);
        Movie movie = requireMovie(request.movieId());
        Hall hall = requireHall(request.hallId());
        requirePriceForEveryCategory(hall, request.prices());
        if (showtimeRepository.existsOverlappingExcluding(hall.getId(), request.startTime(), request.endTime(), id)) {
            throw overlap(hall);
        }
        showtime.update(movie, hall, request.startTime(), request.endTime(), request.prices());
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

    /** Every seat in the hall must be sellable, so each category it uses needs a price. */
    private void requirePriceForEveryCategory(Hall hall, Map<SeatCategory, BigDecimal> prices)
    {
        Set<SeatCategory> missing = EnumSet.noneOf(SeatCategory.class);
        missing.addAll(seatRepository.findCategoriesByHallId(hall.getId()));
        missing.removeAll(prices.keySet());
        if (!missing.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.SHOWTIME_PRICE_MISSING,
                    "Hall '%s' has %s seats, but no price was given for them".formatted(hall.getName(), missing));
        }
    }

    private Showtime require(Long id)
    {
        return showtimeRepository.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.SHOWTIME_NOT_FOUND, "Showtime %d was not found".formatted(id)));
    }

    private Movie requireMovie(Long movieId)
    {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.MOVIE_NOT_FOUND, "Movie %d was not found".formatted(movieId)));
    }

    private Hall requireHall(Long hallId)
    {
        return hallRepository.findById(hallId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.HALL_NOT_FOUND, "Hall %d was not found".formatted(hallId)));
    }

    private static ApiException overlap(Hall hall)
    {
        return ApiException.conflict(ErrorCode.SHOWTIME_OVERLAP,
                "Another showtime is already scheduled in '%s' during this time".formatted(hall.getName()));
    }
}
