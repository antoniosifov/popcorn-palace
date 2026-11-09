package com.antoniosifov.popcornpalace.showtime;

import com.antoniosifov.popcornpalace.common.web.PageResponse;
import com.antoniosifov.popcornpalace.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/showtimes")
@Tag(name = "Showtimes", description = "Screenings of a movie in a hall")
public class ShowtimeController
{
    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService)
    {
        this.showtimeService = showtimeService;
    }

    @GetMapping
    @Operation(summary = "List showtimes", description = "Paginated. Sortable by id, startTime, endTime.")
    public PageResponse<ShowtimeResponse> list(
            @Parameter(description = "Only showtimes of this movie") @RequestParam(required = false) Long movieId,
            @ParameterObject @PageableDefault(size = 20, sort = "startTime") Pageable pageable)
    {
        return showtimeService.list(movieId, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a showtime")
    public ShowtimeResponse get(@PathVariable Long id)
    {
        return showtimeService.get(id);
    }

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Schedule a showtime", description = "Fails with 409 when it overlaps another showtime in the same hall.")
    public ResponseEntity<ShowtimeResponse> create(@Valid @RequestBody ShowtimeRequest request)
    {
        ShowtimeResponse created = showtimeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Replace a showtime", description = "Fails with 409 when it overlaps another showtime in the same hall, or when a showtime with bookings is moved to another hall.")
    public ShowtimeResponse update(@PathVariable Long id, @Valid @RequestBody ShowtimeRequest request)
    {
        return showtimeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Delete a showtime", description = "Fails with 409 while the showtime has bookings.")
    public void delete(@PathVariable Long id)
    {
        showtimeService.delete(id);
    }
}
