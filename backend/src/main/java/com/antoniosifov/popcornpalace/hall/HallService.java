package com.antoniosifov.popcornpalace.hall;

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
public class HallService
{
    static final int MAX_ROWS = 60;
    static final int MAX_SEATS = 1000;

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "rowCount", "seatCount");

    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;

    public HallService(HallRepository hallRepository, SeatRepository seatRepository)
    {
        this.hallRepository = hallRepository;
        this.seatRepository = seatRepository;
    }

    public PageResponse<HallSummaryResponse> list(Pageable pageable)
    {
        SortFields.requireAllowed(pageable, SORTABLE_FIELDS);
        return PageResponse.from(hallRepository.findAll(pageable).map(HallSummaryResponse::from));
    }

    public HallResponse get(Long id)
    {
        Hall hall = require(id);
        return HallResponse.from(hall, seatRepository.findRowsByHallId(id));
    }

    @Transactional
    public HallResponse create(HallRequest request)
    {
        int rowCount = request.sections().stream().mapToInt(HallRequest.Section::rows).sum();
        int seatCount = request.sections().stream().mapToInt(section -> section.rows() * section.seatsPerRow()).sum();
        if (rowCount > MAX_ROWS || seatCount > MAX_SEATS) {
            throw ApiException.badRequest(ErrorCode.HALL_TOO_LARGE,
                    "A hall can have at most %d rows and %d seats; requested %d rows and %d seats"
                            .formatted(MAX_ROWS, MAX_SEATS, rowCount, seatCount));
        }
        if (hallRepository.existsByName(request.name())) {
            throw ApiException.conflict(ErrorCode.HALL_NAME_TAKEN, "A hall named '%s' already exists".formatted(request.name()));
        }

        Hall hall = hallRepository.saveAndFlush(new Hall(request.name(), rowCount, seatCount));

        int nextRow = 1;
        for (HallRequest.Section section : request.sections()) {
            int lastRow = nextRow + section.rows() - 1;
            seatRepository.insertRows(hall.getId(), nextRow, lastRow, section.seatsPerRow(), section.category().name());
            nextRow = lastRow + 1;
        }
        return HallResponse.from(hall, seatRepository.findRowsByHallId(hall.getId()));
    }

    /**
     * Deletes the hall and, through ON DELETE CASCADE, its seats. A hall that
     * still has showtimes cannot be deleted: the foreign key fk_showtimes_hall
     * enforces this and the violation is reported as a conflict.
     */
    @Transactional
    public void delete(Long id)
    {
        hallRepository.delete(require(id));
        hallRepository.flush();
    }

    private Hall require(Long id)
    {
        return hallRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.HALL_NOT_FOUND, "Hall %d was not found".formatted(id)));
    }
}
